package com.majdus.organisateur

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
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

class NoteAdapter(
    private val onClick: (NoteCard) -> Unit,
    private val onLongClick: (NoteCard) -> Unit
) : ListAdapter<NoteCard, NoteAdapter.NoteViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onLongClick)
    }

    class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.card)
        private val title: TextView = view.findViewById(R.id.title)
        private val preview: TextView = view.findViewById(R.id.preview)
        private val date: TextView = view.findViewById(R.id.date)

        fun bind(
            note: NoteCard,
            onClick: (NoteCard) -> Unit,
            onLongClick: (NoteCard) -> Unit
        ) {
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
            card.setOnLongClickListener {
                onLongClick(note)
                true
            }
            card.contentDescription = context.getString(
                R.string.note_item_description,
                note.title.ifBlank { context.getString(R.string.note_untitled) }
            )
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NoteCard>() {
            override fun areItemsTheSame(oldItem: NoteCard, newItem: NoteCard): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: NoteCard, newItem: NoteCard): Boolean =
                oldItem.title == newItem.title &&
                        oldItem.colorKey == newItem.colorKey &&
                        oldItem.updatedAt == newItem.updatedAt &&
                        oldItem.preview.toString() == newItem.preview.toString()
        }
    }
}
