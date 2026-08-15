package com.majdus.organisateur.agenda

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.majdus.organisateur.EventColors
import com.majdus.organisateur.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pages d'une grille horaire, une période par page.
 *
 * L'ancre est une date fixe et la plage bornée à quelques années plutôt que `Int.MAX_VALUE`: une
 * position se traduit alors en date par une simple addition, et « aller à aujourd'hui » devient
 * un `setCurrentItem` au lieu d'une recherche.
 */
class AgendaPagerAdapter(
    private val daysPerPage: Int,
    private val occurrencesOf: (List<LocalDate>) -> List<Occurrence>,
    private val onSlotClick: (LocalDateTime) -> Unit,
    private val onOccurrenceClick: (Occurrence) -> Unit
) : RecyclerView.Adapter<AgendaPagerAdapter.PageHolder>() {

    /**
     * Défilement vertical partagé par les pages.
     *
     * Sans lui, balayer d'un jour à l'autre ramènerait chaque fois la nouvelle page en haut de la
     * journée: on perdrait l'heure qu'on était en train de regarder.
     */
    private var sharedScrollY = -1

    /** Cinquante ans à partir de l'ancre: bien au-delà de ce qu'un agenda personnel parcourt. */
    override fun getItemCount(): Int = 365 * 50 / daysPerPage

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_time_grid, parent, false)
        return PageHolder(view)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val days = daysAt(position)
        holder.bind(days)
    }

    fun daysAt(position: Int): List<LocalDate> {
        val first = ANCHOR.plusDays(position.toLong() * daysPerPage)
        return (0 until daysPerPage).map { first.plusDays(it.toLong()) }
    }

    /** Page contenant [date], en alignant les semaines sur le lundi. */
    fun positionOf(date: LocalDate): Int {
        val aligned = if (daysPerPage > 1) date.minusDays(((date.dayOfWeek.value - 1).toLong())) else date
        return (java.time.temporal.ChronoUnit.DAYS.between(ANCHOR, aligned) / daysPerPage).toInt()
    }

    inner class PageHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dayHeaders: LinearLayout = view.findViewById(R.id.dayHeaders)
        private val allDayBand: LinearLayout = view.findViewById(R.id.allDayBand)
        private val scroller: NestedScrollView = view.findViewById(R.id.scroller)
        private val gutter: HourGutterView = view.findViewById(R.id.gutter)
        private val grid: TimeGridView = view.findViewById(R.id.grid)

        init {
            gutter.hourHeightPx = grid.hourHeightPx
            grid.onSlotClick = { onSlotClick(it) }
            grid.onOccurrenceClick = { onOccurrenceClick(it) }
            scroller.setOnScrollChangeListener { _: NestedScrollView, _: Int, y: Int, _: Int, _: Int ->
                sharedScrollY = y
            }
        }

        fun bind(days: List<LocalDate>) {
            grid.days = days
            renderHeaders(days)

            val occurrences = occurrencesOf(days)
            renderAllDay(days, occurrences.filter { it.allDay })
            renderGrid(days, occurrences.filterNot { it.allDay })
            restoreScroll(occurrences)
        }

        private fun renderHeaders(days: List<LocalDate>) {
            val context = itemView.context
            while (dayHeaders.childCount < days.size) {
                dayHeaders.addView(
                    LayoutInflater.from(context)
                        .inflate(R.layout.item_day_header, dayHeaders, false)
                )
            }
            for (index in 0 until dayHeaders.childCount) {
                val cell = dayHeaders.getChildAt(index)
                val day = days.getOrNull(index)
                cell.visibility = if (day == null) View.GONE else View.VISIBLE
                if (day == null) continue

                val weekday: TextView = cell.findViewById(R.id.weekday)
                val number: TextView = cell.findViewById(R.id.dayNumber)
                weekday.text = day.dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.FRANCE)
                    .uppercase(Locale.FRANCE)
                number.text = day.dayOfMonth.toString()

                // Le jour même porte le même disque ambre que dans la grille du mois: un seul
                // vocabulaire visuel pour « c'est aujourd'hui ».
                val isToday = day == LocalDate.now()
                number.background = if (isToday) {
                    ContextCompat.getDrawable(context, R.drawable.bg_day_selected)
                } else {
                    null
                }
                number.setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (isToday) R.color.white else R.color.text_primary
                    )
                )
            }
        }

        private fun renderAllDay(days: List<LocalDate>, allDay: List<Occurrence>) {
            allDayBand.removeAllViews()
            allDayBand.visibility = if (allDay.isEmpty()) View.GONE else View.VISIBLE
            if (allDay.isEmpty()) return

            val context = itemView.context
            val inflater = LayoutInflater.from(context)
            for (day in days) {
                val column = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val dayStart = Zones.dayStart(day, Zones.current())
                val dayEnd = Zones.dayEnd(day, Zones.current())
                for (occurrence in allDay.filter { it.overlaps(dayStart, dayEnd) }.take(MAX_ALL_DAY)) {
                    val block = inflater.inflate(R.layout.item_all_day_block, column, false) as TextView
                    val color = EventColors.color(context, occurrence.colorKey)
                    block.background?.setTint(ColorUtils.setAlphaComponent(color, BACKGROUND_ALPHA))
                    block.text = occurrence.title
                    block.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    block.setOnClickListener { onOccurrenceClick(occurrence) }
                    column.addView(block)
                }
                allDayBand.addView(column)
            }
        }

        private fun renderGrid(days: List<LocalDate>, timed: List<Occurrence>) {
            val zone = Zones.current()
            val slotsByDay = days.map { day ->
                val dayStart = Zones.dayStart(day, zone)
                val dayEnd = Zones.dayEnd(day, zone)
                OverlapLayout.place(timed.filter { it.overlaps(dayStart, dayEnd) })
            }
            grid.submit(slotsByDay)
        }

        /**
         * Ouvre la page à une heure utile.
         *
         * Le défilement partagé fait foi dès qu'on a bougé; sinon on se cale sur le premier
         * événement, ou sur le début de matinée quand la journée est vide — s'ouvrir à minuit
         * n'apprendrait rien.
         */
        private fun restoreScroll(occurrences: List<Occurrence>) {
            val target = if (sharedScrollY >= 0) {
                sharedScrollY
            } else {
                val zone = Zones.current()
                val firstHour = occurrences.filterNot { it.allDay }
                    .minOfOrNull { Zones.localDateTime(it.startUtc, zone).hour }
                    ?: DEFAULT_HOUR
                (minOf(firstHour, DEFAULT_HOUR) * grid.hourHeightPx).toInt()
            }
            scroller.post { scroller.scrollTo(0, target) }
        }
    }

    companion object {
        /** Ancre arbitraire mais stable: un lundi, pour que les pages de semaine s'y alignent. */
        private val ANCHOR: LocalDate = LocalDate.of(2000, 1, 3)
        private const val MAX_ALL_DAY = 3
        private const val DEFAULT_HOUR = 8
        private const val BACKGROUND_ALPHA = 38
    }
}
