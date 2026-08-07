package com.majdus.organisateur

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Locale

/**
 * Une note telle qu'affichée sur sa carte.
 *
 * [preview] est déjà mis en forme et tronqué: convertir l'AST en `Spanned` coûte un parcours de
 * JSON, ce qui n'a rien à faire dans `onBindViewHolder` — le travail est fait une fois au
 * chargement de la liste, hors du fil principal. Même chose pour [checklist], qui n'est rempli
 * que pour une note de type liste, et pour [remainingItems] qui compte ce qui n'y tient pas.
 */
data class NoteCard(
    val id: String,
    val title: String,
    val preview: CharSequence,
    val checklist: List<ChecklistLine>,
    val remainingItems: Int,
    val colorKey: String,
    val updatedAt: Long
)

/** Une ligne d'aperçu de liste: son texte et sa case. */
data class ChecklistLine(val text: String, val checked: Boolean)

/**
 * Grille des notes, réorganisable au doigt.
 *
 * Volontairement un adaptateur ordinaire et non un `ListAdapter`: ce dernier calcule ses
 * différences en tâche de fond, ce qui convient à un rafraîchissement mais pas à un glisser —
 * chaque franchissement de tuile arriverait avec une image ou deux de retard, et se jouerait en
 * disparition/réapparition au lieu d'un déplacement. [moveItem] agit donc directement sur la
 * liste, sur le fil principal, et [submit] réserve le calcul de différences aux rechargements.
 */
class NoteAdapter(
    private val onClick: (NoteCard) -> Unit
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    private val cards = mutableListOf<NoteCard>()

    override fun getItemCount(): Int = cards.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(cards[position], onClick)
    }

    /**
     * Remplacement du contenu après lecture en base. Le calcul de différences reste sur le fil
     * principal: la grille se compte en dizaines de tuiles, et le faire de façon asynchrone
     * rouvrirait la porte au décalage que [moveItem] existe précisément pour éviter.
     */
    fun submit(newCards: List<NoteCard>) {
        val diff = DiffUtil.calculateDiff(Difference(cards.toList(), newCards), true)
        cards.clear()
        cards.addAll(newCards)
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Déplacement d'une tuile pendant le glisser. `notifyItemMoved` est ce qui fait glisser les
     * autres tuiles vers leur nouvelle place au lieu de les faire clignoter.
     */
    fun moveItem(from: Int, to: Int) {
        if (from !in cards.indices || to !in cards.indices) return
        cards.add(to, cards.removeAt(from))
        notifyItemMoved(from, to)
    }

    /** L'ordre affiché à l'instant, à enregistrer une fois la tuile relâchée. */
    fun currentOrderIds(): List<String> = cards.map { it.id }

    class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.card)
        private val title: TextView = view.findViewById(R.id.title)
        private val preview: TextView = view.findViewById(R.id.preview)
        private val checklistPreview: LinearLayout = view.findViewById(R.id.checklistPreview)
        private val moreItems: TextView = view.findViewById(R.id.moreItems)
        private val date: TextView = view.findViewById(R.id.date)

        fun bind(note: NoteCard, onClick: (NoteCard) -> Unit) {
            val context = itemView.context

            // Une note sans titre, ou sans corps, ne doit pas laisser un blanc sur sa carte.
            title.text = note.title
            title.visibility = if (note.title.isBlank()) View.GONE else View.VISIBLE
            preview.text = note.preview
            preview.visibility = if (note.preview.isBlank()) View.GONE else View.VISIBLE
            bindChecklist(note)

            date.text = DateLabels.relativeDay(context, note.updatedAt)
                .replaceFirstChar { it.titlecase(Locale.FRANCE) }
            card.setCardBackgroundColor(NoteColors.color(context, note.colorKey))

            card.setOnClickListener { onClick(note) }
            // L'appui long revient à `ItemTouchHelper`, qui y démarre le déplacement. Il publie
            // de lui-même les actions d'accessibilité correspondantes; l'indice annoncé à la
            // lecture d'écran, lui, se dit ici.
            val name = note.title.ifBlank { context.getString(R.string.note_untitled) }
            val kind = if (note.checklist.isEmpty()) {
                R.string.note_item_description
            } else {
                R.string.note_checklist_item_description
            }
            card.contentDescription = context.getString(kind, name) +
                    ", " + context.getString(R.string.note_reorder_hint)
        }

        /**
         * Les lignes déjà en place sont réutilisées plutôt que recréées: une carte de liste
         * repasse par ici à chaque défilement, et gonfler quelques vues à chaque fois se verrait
         * sur la fluidité de la grille.
         */
        private fun bindChecklist(note: NoteCard) {
            val context = itemView.context
            checklistPreview.visibility = if (note.checklist.isEmpty()) View.GONE else View.VISIBLE

            while (checklistPreview.childCount < note.checklist.size) {
                LayoutInflater.from(context)
                    .inflate(R.layout.item_note_checkline, checklistPreview, true)
            }
            for (index in 0 until checklistPreview.childCount) {
                val row = checklistPreview.getChildAt(index)
                val line = note.checklist.getOrNull(index)
                if (line == null) {
                    row.visibility = View.GONE
                    continue
                }
                row.visibility = View.VISIBLE
                val glyph = row.findViewById<ImageView>(R.id.glyph)
                val text = row.findViewById<TextView>(R.id.lineText)
                if (line.checked) {
                    // La coche porte déjà la teinte des notes: la reteinter la ferait grise.
                    glyph.setImageResource(R.drawable.ic_check_circle_notes)
                    glyph.imageTintList = null
                } else {
                    glyph.setImageResource(R.drawable.ic_circle_outline)
                    glyph.imageTintList =
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_tertiary))
                }
                text.text = line.text
                text.paintFlags = if (line.checked) {
                    text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
                text.alpha = if (line.checked) CHECKED_ALPHA else 1f
            }

            moreItems.visibility = if (note.remainingItems > 0) View.VISIBLE else View.GONE
            if (note.remainingItems > 0) {
                moreItems.text = if (note.remainingItems == 1) {
                    context.getString(R.string.note_checklist_more_one)
                } else {
                    context.getString(R.string.note_checklist_more_other, note.remainingItems)
                }
            }
        }

        private companion object {
            const val CHECKED_ALPHA = 0.55f
        }
    }

    private class Difference(
        private val oldCards: List<NoteCard>,
        private val newCards: List<NoteCard>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldCards.size

        override fun getNewListSize(): Int = newCards.size

        override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
            oldCards[oldPosition].id == newCards[newPosition].id

        override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
            val old = oldCards[oldPosition]
            val new = newCards[newPosition]
            return old.title == new.title &&
                    old.colorKey == new.colorKey &&
                    old.updatedAt == new.updatedAt &&
                    old.remainingItems == new.remainingItems &&
                    old.checklist == new.checklist &&
                    old.preview.toString() == new.preview.toString()
        }
    }
}
