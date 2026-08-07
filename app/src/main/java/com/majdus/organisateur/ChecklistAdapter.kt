package com.majdus.organisateur

import android.annotation.SuppressLint
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/**
 * La liste à cocher de l'éditeur de note, sur le modèle de Keep.
 *
 * Trois sortes de rangées se succèdent toujours dans le même ordre: les éléments non cochés, la
 * ligne d'ajout, puis — s'il y en a — l'en-tête « n éléments cochés » et les éléments cochés.
 * [items] est la source unique, tenue dans cet ordre par [Checklist.normalize]; les rangées
 * n'en sont qu'une vue, recalculée à chaque changement de structure.
 *
 * La saisie, elle, ne passe jamais par une notification: le champ écrit directement dans son
 * élément. Redessiner une rangée dont on est en train de remplir le champ ferait sauter le
 * curseur à chaque lettre.
 */
class ChecklistAdapter(
    private val items: MutableList<ChecklistItem>,
    private val onChanged: () -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Section des cochés dépliée ? Comme dans Keep, on la replie pour dégager ce qui reste. */
    private var checkedExpanded = true

    private var rows: List<Row> = buildRows()

    /** Champ à qui rendre la main dès que sa rangée est liée: nouvelle ligne, ligne fusionnée… */
    private var pendingFocus: PendingFocus? = null

    private var recyclerView: RecyclerView? = null

    init {
        // Identités stables: la RecyclerView sait alors rendre le focus au bon champ après un
        // changement de structure, au lieu de le laisser tomber.
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemId(position: Int): Long = rows[position].key.hashCode().toLong()

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Entry -> TYPE_ENTRY
        is Row.Header -> TYPE_HEADER
        Row.Add -> TYPE_ADD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_ENTRY -> EntryHolder(inflater.inflate(R.layout.item_checklist_row, parent, false))
            TYPE_HEADER -> HeaderHolder(
                inflater.inflate(R.layout.item_checklist_header, parent, false)
            )
            else -> AddHolder(inflater.inflate(R.layout.item_checklist_add, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Entry -> (holder as EntryHolder).bind(row.item)
            is Row.Header -> (holder as HeaderHolder).bind(row.count, row.expanded)
            Row.Add -> Unit
        }
    }

    // ===== Ce que l'éditeur pilote =====

    /** Reprise complète après un chargement ou une conversion: plus rien n'a de rapport. */
    @SuppressLint("NotifyDataSetChanged")
    fun reload() {
        Checklist.normalize(items)
        checkedExpanded = true
        pendingFocus = null
        rows = buildRows()
        notifyDataSetChanged()
    }

    /** Ajoute une ligne vide en fin de section active et y place le curseur. */
    fun appendItem() {
        val created = ChecklistItem()
        items.add(uncheckedCount(), created)
        pendingFocus = PendingFocus(created.id, 0)
        refresh()
        onChanged()
    }

    fun hasCheckedItems(): Boolean = items.any { it.checked }

    /** Les éléments décochés reprennent la file, dans leur ordre. */
    fun uncheckAll() {
        if (!hasCheckedItems()) return
        items.forEach { it.checked = false }
        refresh()
        onChanged()
    }

    /** Renvoie le nombre d'éléments retirés, pour l'annonce faite à l'utilisateur. */
    fun deleteCheckedItems(): Int {
        val removed = items.count { it.checked }
        if (removed == 0) return 0
        items.removeAll { it.checked }
        refresh()
        onChanged()
        return removed
    }

    // ===== Déplacement au doigt =====

    /** Seuls les éléments se déplacent: ni la ligne d'ajout, ni l'en-tête des cochés. */
    fun isMovable(position: Int): Boolean = rows.getOrNull(position) is Row.Entry

    fun canSwap(from: Int, to: Int): Boolean {
        val source = rows.getOrNull(from) as? Row.Entry ?: return false
        val target = rows.getOrNull(to) as? Row.Entry ?: return false
        return source.checked == target.checked
    }

    /**
     * Déplacement en cours de glisser: notification directe, sans passer par le calcul de
     * différences — c'est ce qui fait glisser les lignes au lieu de les faire clignoter.
     */
    fun moveRow(from: Int, to: Int) {
        val source = rows.getOrNull(from) as? Row.Entry ?: return
        val target = rows.getOrNull(to) as? Row.Entry ?: return
        val fromIndex = indexOf(source.item)
        val toIndex = indexOf(target.item)
        if (fromIndex < 0 || toIndex < 0) return

        items.add(toIndex, items.removeAt(fromIndex))
        rows = buildRows()
        notifyItemMoved(from, to)
    }

    // ===== Mutations venues des rangées =====

    private fun setChecked(item: ChecklistItem, checked: Boolean) {
        val index = indexOf(item)
        if (index < 0) return

        items.removeAt(index)
        item.checked = checked
        // Cocher pousse la ligne juste sous l'en-tête des cochés, décocher la ramène à la fin des
        // éléments actifs: dans les deux cas, la frontière entre les deux sections.
        items.add(uncheckedCount(), item)
        refresh()
        onChanged()
    }

    private fun removeItem(item: ChecklistItem) {
        val index = indexOf(item)
        if (index < 0) return
        items.removeAt(index)
        refresh()
        onChanged()
    }

    /**
     * Entrée: la ligne est coupée au curseur, la fin part dans une nouvelle ligne juste dessous.
     * Couper au curseur et non en bout de ligne permet d'insérer un élément oublié au milieu
     * d'une liste sans avoir à retaper la suite.
     */
    private fun splitItem(item: ChecklistItem, caret: Int): Boolean {
        val index = indexOf(item)
        if (index < 0) return false
        // Entrée sur une ligne encore vide ne fait rien, mais consomme la touche: il n'y a rien
        // à couper, et cela écarte du même coup les claviers qui délivrent la validation deux
        // fois — à la ligne d'origine puis à celle qui vient d'être ouverte.
        if (item.text.isEmpty()) return true

        val cut = caret.coerceIn(0, item.text.length)
        val created = ChecklistItem(text = item.text.substring(cut), checked = item.checked)
        item.text = item.text.substring(0, cut)
        items.add(index + 1, created)
        pendingFocus = PendingFocus(created.id, 0)
        refresh()
        onChanged()
        return true
    }

    /**
     * Retour arrière en début de ligne: la ligne rejoint la précédente, curseur posé à la
     * jonction. Refusé en tête de section — il n'y a rien au-dessus à quoi se raccrocher, et la
     * frontière des cochés ne se franchit pas à la touche d'effacement.
     */
    private fun mergeIntoPrevious(item: ChecklistItem): Boolean {
        val index = indexOf(item)
        if (index <= 0) return false
        val previous = items[index - 1]
        if (previous.checked != item.checked) return false

        val caret = previous.text.length
        previous.text += item.text
        items.removeAt(index)
        pendingFocus = PendingFocus(previous.id, caret)
        refresh()
        onChanged()
        return true
    }

    private fun toggleCheckedSection() {
        checkedExpanded = !checkedExpanded
        refresh()
    }

    // ===== Rangées =====

    private fun uncheckedCount(): Int = items.count { !it.checked }

    private fun indexOf(item: ChecklistItem): Int = items.indexOfFirst { it === item }

    private fun buildRows(): List<Row> {
        val rows = mutableListOf<Row>()
        val checked = mutableListOf<Row.Entry>()
        for (item in items) {
            val row = Row.Entry(item.id, item.text, item.checked, item)
            if (item.checked) checked += row else rows += row
        }
        rows += Row.Add
        if (checked.isNotEmpty()) {
            rows += Row.Header(checked.size, checkedExpanded)
            if (checkedExpanded) rows += checked
        }
        return rows
    }

    /**
     * Changement de structure: les rangées sont recalculées et la différence dispatchée, ce qui
     * anime la ligne cochée jusqu'à sa nouvelle section au lieu de la faire disparaître.
     */
    private fun refresh() {
        val previous = rows
        rows = buildRows()
        DiffUtil.calculateDiff(RowDifference(previous, rows), true).dispatchUpdatesTo(this)
        scrollToPendingFocus()
    }

    /**
     * Une ligne créée hors de l'écran ne serait jamais liée, donc jamais mise au point: on la
     * ramène d'abord dans le champ de vision.
     */
    private fun scrollToPendingFocus() {
        val focus = pendingFocus ?: return
        val list = recyclerView ?: return
        if (list.isComputingLayout) return
        val position = rows.indexOfFirst { it is Row.Entry && it.id == focus.itemId }
        if (position >= 0) list.scrollToPosition(position)
    }

    private data class PendingFocus(val itemId: String, val caret: Int)

    private sealed interface Row {
        val key: String

        /** [item] est l'élément vivant; [text] et [checked] en sont la photographie à comparer. */
        data class Entry(
            val id: String,
            val text: String,
            val checked: Boolean,
            val item: ChecklistItem
        ) : Row {
            override val key: String get() = id
        }

        data class Header(val count: Int, val expanded: Boolean) : Row {
            override val key: String get() = "header"
        }

        data object Add : Row {
            override val key: String get() = "add"
        }
    }

    private class RowDifference(
        private val oldRows: List<Row>,
        private val newRows: List<Row>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldRows.size

        override fun getNewListSize(): Int = newRows.size

        override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
            oldRows[oldPosition].key == newRows[newPosition].key

        override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
            val old = oldRows[oldPosition]
            val new = newRows[newPosition]
            return when {
                old is Row.Entry && new is Row.Entry ->
                    old.text == new.text && old.checked == new.checked
                old is Row.Header && new is Row.Header ->
                    old.count == new.count && old.expanded == new.expanded
                else -> true
            }
        }
    }

    // ===== Titulaires de rangée =====

    @SuppressLint("ClickableViewAccessibility")
    private inner class EntryHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val handle: ImageView = view.findViewById(R.id.dragHandle)
        private val checkbox: CheckBox = view.findViewById(R.id.checkbox)
        private val text: ChecklistEditText = view.findViewById(R.id.itemText)
        private val remove: ImageView = view.findViewById(R.id.removeItem)

        /** Nul pendant le remplissage du champ: la saisie ne doit pas s'écrire dans l'élément. */
        private var bound: ChecklistItem? = null

        init {
            text.addTextChangedListener { editable ->
                val item = bound ?: return@addTextChangedListener
                item.text = editable?.toString().orEmpty()
                onChanged()
            }
            text.onEnter = { bound?.let { splitItem(it, text.selectionStart) } == true }
            text.onBackspaceAtStart = { bound?.let { mergeIntoPrevious(it) } == true }

            // Le clic plutôt que le changement d'état: la case est aussi cochée par le code au
            // moment de lier la rangée, et un écouteur de changement rejouerait le déplacement.
            checkbox.setOnClickListener {
                val item = bound
                if (item == null) checkbox.isChecked = false else setChecked(item, checkbox.isChecked)
            }
            remove.setOnClickListener { bound?.let { removeItem(it) } }
            handle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(this)
                false
            }
        }

        fun bind(item: ChecklistItem) {
            bound = null
            // Le champ n'est réécrit que s'il diverge vraiment: le réécrire à l'identique
            // déplacerait le curseur de l'utilisateur en train d'y taper.
            if (text.text?.toString() != item.text) text.setText(item.text)
            checkbox.isChecked = item.checked
            text.paintFlags = if (item.checked) {
                text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            text.alpha = if (item.checked) CHECKED_ALPHA else 1f
            bound = item

            val focus = pendingFocus
            if (focus != null && focus.itemId == item.id) {
                pendingFocus = null
                giveFocus(focus.caret)
            }
        }

        /**
         * Mise au point différée: demander le focus pendant la liaison le ferait reprendre par
         * la mise en page qui suit.
         */
        private fun giveFocus(caret: Int) {
            text.post {
                if (!text.requestFocus()) return@post
                text.setSelection(caret.coerceIn(0, text.text?.length ?: 0))
                ViewCompat.getWindowInsetsController(text)?.show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    private inner class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val header: View = view.findViewById(R.id.checkedHeader)
        private val icon: ImageView = view.findViewById(R.id.expandIcon)
        private val count: TextView = view.findViewById(R.id.checkedCount)

        init {
            header.setOnClickListener { toggleCheckedSection() }
        }

        fun bind(checkedCount: Int, expanded: Boolean) {
            val context = itemView.context
            count.text = if (checkedCount == 1) {
                context.getString(R.string.note_checklist_checked_one)
            } else {
                context.getString(R.string.note_checklist_checked_other, checkedCount)
            }
            // Chevron vers le bas quand la section est repliée, vers le haut quand elle est ouverte.
            icon.rotation = if (expanded) 180f else 0f
            header.contentDescription = context.getString(
                if (expanded) R.string.note_checklist_collapse else R.string.note_checklist_expand
            )
        }
    }

    private inner class AddHolder(view: View) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener { appendItem() }
        }
    }

    private companion object {
        const val TYPE_ENTRY = 0
        const val TYPE_ADD = 1
        const val TYPE_HEADER = 2

        /** Un élément coché s'efface sans disparaître: barré et pâli, comme dans Keep. */
        const val CHECKED_ALPHA = 0.5f
    }
}
