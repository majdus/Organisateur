package com.majdus.organisateur

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.util.Locale

/**
 * Une note telle qu'affichée sur sa carte.
 *
 * [preview] est déjà mis en forme et tronqué: convertir l'AST en `Spanned` coûte un parcours de
 * JSON, ce qui n'a rien à faire dans `onBindViewHolder` — le travail est fait une fois au
 * chargement de la liste, hors du fil principal.
 */
data class NoteCard(
    val id: String,
    val title: String,
    val preview: CharSequence,
    val colorKey: String,
    val updatedAt: Long
)

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
        private val date: TextView = view.findViewById(R.id.date)

        fun bind(note: NoteCard, onClick: (NoteCard) -> Unit) {
            val context = itemView.context

            // Une note sans titre, ou sans corps, ne doit pas laisser un blanc sur sa carte.
            title.text = note.title
            title.visibility = if (note.title.isBlank()) View.GONE else View.VISIBLE
            preview.text = note.preview
            preview.visibility = if (note.preview.isBlank()) View.GONE else View.VISIBLE

            date.text = DateLabels.relativeDay(context, note.updatedAt)
                .replaceFirstChar { it.titlecase(Locale.FRANCE) }
            card.setCardBackgroundColor(NoteColors.color(context, note.colorKey))

            card.setOnClickListener { onClick(note) }
            // L'appui long revient à `ItemTouchHelper`, qui y démarre le déplacement. Il publie
            // de lui-même les actions d'accessibilité correspondantes; l'indice annoncé à la
            // lecture d'écran, lui, se dit ici.
            val name = note.title.ifBlank { context.getString(R.string.note_untitled) }
            card.contentDescription = context.getString(R.string.note_item_description, name) +
                    ", " + context.getString(R.string.note_reorder_hint)
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
                    old.preview.toString() == new.preview.toString()
        }
    }
}
