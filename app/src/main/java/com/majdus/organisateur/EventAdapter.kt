package com.majdus.organisateur

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.majdus.organisateur.agenda.Occurrence

/**
 * Une ligne de l'agenda: l'occurrence et ce que la liste doit en dire.
 *
 * Les rappels vivent dans leur propre table, donc l'occurrence seule ne suffit pas à savoir s'il
 * faut afficher la puce de notification.
 */
data class EventRow(val occurrence: Occurrence, val hasReminder: Boolean)

/** Liste des événements du jour sélectionné. */
class EventAdapter(
    private val onClick: (EventRow) -> Unit
) : ListAdapter<EventRow, EventAdapter.EventViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.card)
        private val label: TextView = view.findViewById(R.id.label)
        private val time: TextView = view.findViewById(R.id.timeLabel)
        private val notificationChip: View = view.findViewById(R.id.notificationChip)

        fun bind(row: EventRow, onClick: (EventRow) -> Unit) {
            val occurrence = row.occurrence
            val timeText = DateLabels.time(occurrence.startUtc)
            time.text = timeText
            label.text = occurrence.title
            notificationChip.visibility = if (row.hasReminder) View.VISIBLE else View.GONE

            // Un événement déjà passé reste consultable, mais s'efface derrière ceux à venir.
            val isPast = occurrence.startUtc <= System.currentTimeMillis()
            val contentAlpha = if (isPast) 0.5f else 1f
            time.alpha = contentAlpha
            label.alpha = contentAlpha
            notificationChip.alpha = contentAlpha

            card.setOnClickListener { onClick(row) }
            card.contentDescription = itemView.context.getString(
                R.string.event_item_description, occurrence.title, timeText
            )
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<EventRow>() {
            // Une occurrence s'identifie par sa série *et* sa date: deux occurrences d'une même
            // série partagent leur identifiant d'événement.
            override fun areItemsTheSame(oldItem: EventRow, newItem: EventRow): Boolean =
                oldItem.occurrence.eventId == newItem.occurrence.eventId &&
                        oldItem.occurrence.occurrenceStartUtc == newItem.occurrence.occurrenceStartUtc

            override fun areContentsTheSame(oldItem: EventRow, newItem: EventRow): Boolean =
                oldItem.occurrence == newItem.occurrence &&
                        oldItem.hasReminder == newItem.hasReminder
        }
    }
}
