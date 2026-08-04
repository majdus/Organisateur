package com.majdus.organisateur

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.majdus.organisateur.data.Event
import java.util.Locale

/** Liste des événements du jour sélectionné. */
class EventAdapter(
    private val onClick: (Event) -> Unit
) : ListAdapter<Event, EventAdapter.EventViewHolder>(DIFF_CALLBACK) {

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

        fun bind(event: Event, onClick: (Event) -> Unit) {
            val timeText = String.format(Locale.FRANCE, "%02d:%02d", event.hour, event.minute)
            time.text = timeText
            label.text = event.title
            notificationChip.visibility = if (event.hasNotification) View.VISIBLE else View.GONE

            // Un événement déjà passé reste consultable, mais s'efface derrière ceux à venir.
            val isPast = EventTimes.at(event) <= System.currentTimeMillis()
            val contentAlpha = if (isPast) 0.5f else 1f
            time.alpha = contentAlpha
            label.alpha = contentAlpha
            notificationChip.alpha = contentAlpha

            card.setOnClickListener { onClick(event) }
            card.contentDescription = itemView.context.getString(
                R.string.event_item_description, event.title, timeText
            )
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Event>() {
            override fun areItemsTheSame(oldItem: Event, newItem: Event): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Event, newItem: Event): Boolean =
                oldItem.title == newItem.title &&
                        oldItem.date == newItem.date &&
                        oldItem.hour == newItem.hour &&
                        oldItem.minute == newItem.minute &&
                        oldItem.hasNotification == newItem.hasNotification
        }
    }
}
