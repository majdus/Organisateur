package com.majdus.organisateur

import android.content.Context
import android.graphics.Color
import android.icu.util.Calendar
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Calendrier mensuel maison.
 *
 * `android.widget.CalendarView` impose ses propres polices, couleurs et marges, qu'aucun
 * thème ne reprend: il détonnait avec le reste de l'application et ne sait pas afficher de
 * pastille d'événement. Cette vue dessine six semaines de cellules réutilisées d'un mois à
 * l'autre — aucune allocation pendant la navigation.
 *
 * Les curseurs sont des `android.icu.util.Calendar` et non des `java.util.Calendar`: seule cette
 * famille sait compter en mois hijri. Les deux systèmes empruntent ainsi le même code de rendu,
 * la fabrique [CalendarSystems.newCalendar] étant leur unique point de divergence. Les instants
 * produits, eux, ne dépendent d'aucun calendrier: la clé de stockage reste grégorienne.
 */
class MonthCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Appelé quand l'utilisateur choisit un jour (valeur: minuit ce jour-là). */
    var onDateSelected: ((Long) -> Unit)? = null

    /** Appelé quand le mois affiché change: la plage visible est à recharger. */
    var onMonthChanged: (() -> Unit)? = null

    private val monthLabel: TextView
    private val monthSubtitle: TextView
    private val weeksContainer: LinearLayout
    private val cells = ArrayList<DayCell>(TOTAL_CELLS)
    private val rows = ArrayList<View>(WEEKS)

    /**
     * Système calendaire affiché. En changer conserve le jour réel sélectionné: on ne fait que
     * le regarder sous un autre découpage.
     */
    var calendarSystem: CalendarSystem = CalendarSystems.DEFAULT
        set(value) {
            if (field == value) return
            field = value
            rebuildCursors()
            render()
        }

    private var displayedMonth: Calendar = newCalendar(System.currentTimeMillis()).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    private var selectedDate: Calendar = newCalendar(System.currentTimeMillis())
    private var markedDates: Set<String> = emptySet()

    private val colorPrimary = ContextCompat.getColor(context, R.color.text_primary)
    private val colorMuted = ContextCompat.getColor(context, R.color.text_tertiary)
    private val colorAccent = ContextCompat.getColor(context, R.color.accent_calendar)

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_month_calendar, this, true)
        monthLabel = findViewById(R.id.monthLabel)
        monthSubtitle = findViewById(R.id.monthSubtitle)
        weeksContainer = findViewById(R.id.weeks)

        findViewById<ImageButton>(R.id.previousMonth).setOnClickListener { shiftMonth(-1) }
        findViewById<ImageButton>(R.id.nextMonth).setOnClickListener { shiftMonth(1) }

        buildWeekdayHeader()
        buildCells()
        render()
    }

    /** Jour actuellement sélectionné (minuit). */
    val selectedTimeInMillis: Long get() = selectedDate.timeInMillis

    /**
     * Bornes des jours affichés, débordements sur les mois voisins compris: c'est la plage
     * dont il faut connaître les événements pour poser toutes les pastilles.
     */
    fun visibleRange(): Pair<String, String> {
        val start = firstVisibleDay()
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, TOTAL_CELLS - 1) }
        // Clés grégoriennes quel que soit le calendrier affiché: c'est le format de la base.
        return DateLabels.key(start.timeInMillis) to DateLabels.key(end.timeInMillis)
    }

    /** Bornes du mois affiché, pour le décompte du résumé. */
    fun monthRange(): Pair<String, String> {
        val start = displayedMonth.clone() as Calendar
        val end = (start.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, start.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        return DateLabels.key(start.timeInMillis) to DateLabels.key(end.timeInMillis)
    }

    /** Sélectionne un jour, en amenant le mois affiché sur lui si besoin. */
    fun selectDate(timeInMillis: Long, notify: Boolean = false) {
        val target = newCalendar(timeInMillis)
        val monthChanged = target.get(Calendar.YEAR) != displayedMonth.get(Calendar.YEAR) ||
                target.get(Calendar.MONTH) != displayedMonth.get(Calendar.MONTH)
        selectedDate = target
        if (monthChanged) {
            displayedMonth.timeInMillis = target.timeInMillis
            displayedMonth.set(Calendar.DAY_OF_MONTH, 1)
        }
        render()
        if (notify) onDateSelected?.invoke(selectedDate.timeInMillis)
        if (monthChanged) onMonthChanged?.invoke()
    }

    /** Jours portant au moins un événement, au format "yyyy-MM-dd". */
    fun setMarkedDates(dates: Set<String>) {
        markedDates = dates
        render()
    }

    private fun shiftMonth(offset: Int) {
        displayedMonth.add(Calendar.MONTH, offset)
        render()
        onMonthChanged?.invoke()
    }

    private fun buildWeekdayHeader() {
        val header = findViewById<LinearLayout>(R.id.weekdays)
        for (label in WEEKDAY_LABELS) {
            val view = TextView(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER
                text = label
                textSize = 12f
                setTextColor(colorMuted)
            }
            header.addView(view)
        }
    }

    private fun buildCells() {
        val inflater = LayoutInflater.from(context)
        repeat(WEEKS) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
            repeat(DAYS_PER_WEEK) {
                val cellView = inflater.inflate(R.layout.item_calendar_day, row, false)
                (cellView.layoutParams as LayoutParams).weight = 1f
                row.addView(cellView)
                cells.add(
                    DayCell(
                        root = cellView,
                        day = cellView.findViewById(R.id.day),
                        gregorianDay = cellView.findViewById(R.id.gregorianDay),
                        dot = cellView.findViewById(R.id.dot)
                    )
                )
            }
            weeksContainer.addView(row)
            rows.add(row)
        }
    }

    /** Curseurs recréés dans le système courant, aux mêmes instants. */
    private fun rebuildCursors() {
        val selected = selectedDate.timeInMillis
        selectedDate = newCalendar(selected)
        // Le mois affiché se cale sur celui qui contient le jour sélectionné: changer de
        // calendrier ne doit pas faire perdre la journée qu'on regardait.
        displayedMonth = newCalendar(selected).apply { set(Calendar.DAY_OF_MONTH, 1) }
    }

    /** Curseur du système courant, ramené à minuit. */
    private fun newCalendar(timeInMillis: Long): Calendar =
        CalendarSystems.newCalendar(calendarSystem).apply {
            this.timeInMillis = timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun render() {
        monthLabel.text =
            CalendarSystems.monthTitle(context, calendarSystem, displayedMonth.timeInMillis)

        val subtitle = CalendarSystems.monthSubtitle(
            context,
            calendarSystem,
            displayedMonth.timeInMillis,
            lastDayOfDisplayedMonth()
        )
        monthSubtitle.text = subtitle.orEmpty()
        monthSubtitle.visibility = if (subtitle == null) View.GONE else View.VISIBLE

        // Un mois tient sur cinq semaines la plupart du temps: afficher une sixième rangée
        // vide volerait de la hauteur à l'agenda du jour, juste en dessous.
        val weeksNeeded = weeksNeeded()
        rows.forEachIndexed { index, row ->
            row.visibility = if (index < weeksNeeded) View.VISIBLE else View.GONE
        }

        val cursor = firstVisibleDay()
        val displayedMonthIndex = displayedMonth.get(Calendar.MONTH)
        for (cell in cells) {
            val timestamp = cursor.timeInMillis
            val inMonth = cursor.get(Calendar.MONTH) == displayedMonthIndex
            val isSelected = isSameDay(cursor, selectedDate)
            val isToday = DateLabels.isToday(timestamp)
            val isMarked = markedDates.contains(DateLabels.key(timestamp))

            cell.day.text = cursor.get(Calendar.DAY_OF_MONTH).toString()
            // En hijri, la case porte aussi son équivalent grégorien: on garde le pied dans le
            // calendrier civil sans avoir à convertir de tête.
            val showsGregorian = calendarSystem == CalendarSystem.HIJRI
            cell.gregorianDay.visibility = if (showsGregorian) View.VISIBLE else View.GONE
            if (showsGregorian) {
                cell.gregorianDay.text = CalendarSystems.gregorianDayLabel(timestamp)
                cell.gregorianDay.setTextColor(
                    if (isSelected || isToday) colorAccent else colorMuted
                )
                cell.gregorianDay.alpha = if (inMonth) 1f else 0.5f
            }
            cell.day.background = when {
                isSelected -> ContextCompat.getDrawable(context, R.drawable.bg_day_selected)
                isToday -> ContextCompat.getDrawable(context, R.drawable.bg_day_today)
                else -> null
            }
            cell.day.setTextColor(
                when {
                    isSelected -> Color.WHITE
                    isToday -> colorAccent
                    inMonth -> colorPrimary
                    else -> colorMuted
                }
            )
            // Un jour hors du mois affiché reste lisible, mais ne doit pas capter le regard.
            cell.day.alpha = if (inMonth) 1f else 0.5f
            cell.dot.visibility = if (isMarked) View.VISIBLE else View.INVISIBLE
            cell.dot.alpha = if (inMonth) 1f else 0.5f

            cell.root.contentDescription = context.getString(
                R.string.calendar_day_description,
                CalendarSystems.dayHeader(context, calendarSystem, timestamp),
                if (isMarked) context.getString(R.string.calendar_day_has_events) else ""
            )
            cell.root.setOnClickListener { selectDate(timestamp, notify = true) }

            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    /** Dernier jour du mois affiché, pour dire quels mois grégoriens il recouvre. */
    private fun lastDayOfDisplayedMonth(): Long = (displayedMonth.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
    }.timeInMillis

    /** Nombre de rangées nécessaires pour couvrir le mois affiché (5, parfois 4 ou 6). */
    private fun weeksNeeded(): Int {
        val offset = (displayedMonth.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        val daysInMonth = displayedMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (offset + daysInMonth + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK
    }

    /** Lundi de la semaine contenant le 1er du mois affiché. */
    private fun firstVisibleDay(): Calendar {
        val cursor = displayedMonth.clone() as Calendar
        // Calendar.MONDAY vaut 2, DIMANCHE 1: on ramène l'écart dans 0..6 en partant du lundi.
        val offset = (cursor.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        cursor.add(Calendar.DAY_OF_MONTH, -offset)
        return cursor
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean =
        first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
                first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

    private class DayCell(
        val root: View,
        val day: TextView,
        val gregorianDay: TextView,
        val dot: View
    )

    private companion object {
        const val WEEKS = 6
        const val DAYS_PER_WEEK = 7
        const val TOTAL_CELLS = WEEKS * DAYS_PER_WEEK
        val WEEKDAY_LABELS = listOf("L", "M", "M", "J", "V", "S", "D")
    }
}
