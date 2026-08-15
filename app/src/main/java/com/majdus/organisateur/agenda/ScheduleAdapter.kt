package com.majdus.organisateur.agenda

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.majdus.organisateur.DateLabels
import com.majdus.organisateur.EventColors
import com.majdus.organisateur.R
import java.time.LocalDate
import java.util.Locale

/** Une ligne du planning: un en-tête de jour, ou une occurrence. */
sealed interface ScheduleItem {
    data class Header(val date: LocalDate) : ScheduleItem
    data class Row(val occurrence: Occurrence) : ScheduleItem
}

/**
 * Liste chronologique continue, regroupée par jour.
 *
 * C'est la vue qui répond à « qu'est-ce qui vient ensuite » sans imposer de choisir une période:
 * les journées vides sont simplement absentes, au lieu d'occuper de la place à ne rien montrer.
 */
class ScheduleAdapter(
    private val onClick: (Occurrence) -> Unit
) : ListAdapter<ScheduleItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is ScheduleItem.Header) TYPE_HEADER else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_schedule_header, parent, false))
        } else {
            RowHolder(inflater.inflate(R.layout.item_schedule_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ScheduleItem.Header -> (holder as HeaderHolder).bind(item.date)
            is ScheduleItem.Row -> (holder as RowHolder).bind(item.occurrence, onClick)
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.dayLabel)

        fun bind(date: LocalDate) {
            // Midi: jamais ambigu, quelle que soit la nuit du changement d'heure.
            val noon = Zones.toUtc(date.atTime(12, 0), Zones.current())
            val text = if (DateLabels.isToday(noon)) {
                itemView.context.getString(
                    R.string.calendar_section_today, DateLabels.weekday(noon)
                )
            } else {
                DateLabels.weekday(noon)
            }
            label.text = text.replaceFirstChar { it.titlecase(Locale.FRANCE) }
        }
    }

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val stripe: View = view.findViewById(R.id.stripe)
        private val time: TextView = view.findViewById(R.id.time)
        private val title: TextView = view.findViewById(R.id.title)
        private val location: TextView = view.findViewById(R.id.location)

        fun bind(occurrence: Occurrence, onClick: (Occurrence) -> Unit) {
            val context = itemView.context
            stripe.background?.setTint(EventColors.color(context, occurrence.colorKey))
            time.text = if (occurrence.allDay) {
                context.getString(R.string.agenda_all_day_short)
            } else {
                DateLabels.time(occurrence.startUtc)
            }
            title.text = occurrence.title
            location.text = occurrence.location
            location.visibility = if (occurrence.location.isEmpty()) View.GONE else View.VISIBLE

            // Un événement passé reste consultable, mais s'efface derrière ceux à venir.
            val alpha = if (occurrence.endUtc <= System.currentTimeMillis()) 0.5f else 1f
            itemView.alpha = alpha

            title.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            itemView.setOnClickListener { onClick(occurrence) }
            itemView.contentDescription = context.getString(
                R.string.event_item_description,
                occurrence.title,
                DateLabels.time(occurrence.startUtc)
            )
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1

        /** Découpe une suite d'occurrences en journées, en-têtes compris. */
        fun itemsOf(occurrences: List<Occurrence>, zone: java.time.ZoneId): List<ScheduleItem> {
            val items = ArrayList<ScheduleItem>()
            var currentDay: LocalDate? = null
            for (occurrence in occurrences.sortedWith(
                compareBy<Occurrence> { it.startUtc }.thenByDescending { it.allDay }
            )) {
                val day = Zones.localDate(occurrence.startUtc, zone)
                if (day != currentDay) {
                    items.add(ScheduleItem.Header(day))
                    currentDay = day
                }
                items.add(ScheduleItem.Row(occurrence))
            }
            return items
        }

        private val DIFF = object : DiffUtil.ItemCallback<ScheduleItem>() {
            override fun areItemsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem): Boolean =
                when {
                    oldItem is ScheduleItem.Header && newItem is ScheduleItem.Header ->
                        oldItem.date == newItem.date
                    oldItem is ScheduleItem.Row && newItem is ScheduleItem.Row ->
                        oldItem.occurrence.eventId == newItem.occurrence.eventId &&
                                oldItem.occurrence.occurrenceStartUtc ==
                                newItem.occurrence.occurrenceStartUtc
                    else -> false
                }

            override fun areContentsTheSame(oldItem: ScheduleItem, newItem: ScheduleItem): Boolean =
                oldItem == newItem
        }
    }
}
