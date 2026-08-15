package com.majdus.organisateur

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.majdus.organisateur.agenda.AgendaPagerAdapter
import com.majdus.organisateur.agenda.EventDraft
import com.majdus.organisateur.agenda.Occurrence
import com.majdus.organisateur.agenda.ScheduleAdapter
import com.majdus.organisateur.agenda.Zones
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.EditScope
import com.majdus.organisateur.data.Event
import com.majdus.organisateur.data.EventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * L'agenda: la même donnée sous trois découpages.
 *
 * Une seule activité, sans fragments. Les trois vues partagent la même liste d'occurrences, la
 * même barre d'outils et le même bouton d'ajout: des fragments n'apporteraient qu'un second cycle
 * de vie à tenir. Le basculement se fait par visibilité sur des racines déjà construites, donc
 * sans réinflation et en gardant les positions de défilement.
 *
 * L'activité orchestre: [EventRepository] détient l'état et déplie les séries, [EventEditSheet]
 * la saisie, [EventAlarmScheduler] les rappels.
 */
class Agenda : AppCompatActivity() {

    private val repository by lazy { EventRepository(AppDatabase.getDatabase(this)) }

    private lateinit var viewModeGroup: MaterialButtonToggleGroup
    private lateinit var timePager: ViewPager2
    private lateinit var monthHeader: View
    private lateinit var monthList: View
    private lateinit var scheduleRecycler: RecyclerView
    private lateinit var scheduleEmpty: View
    private lateinit var calendarView: MonthCalendarView
    private lateinit var recyclerView: RecyclerView
    private lateinit var summaryView: TextView
    private lateinit var sectionLabel: TextView
    private lateinit var sectionSubtitle: TextView
    private lateinit var emptyState: View
    private lateinit var addButton: ExtendedFloatingActionButton
    private lateinit var eventAdapter: EventAdapter
    private lateinit var scheduleAdapter: ScheduleAdapter

    private var viewMode = AgendaView.DEFAULT
    private var pagerAdapter: AgendaPagerAdapter? = null

    /** Jour affiché dans l'agenda du mode mois, au format "yyyy-MM-dd". */
    private var selectedDate: String = DateLabels.key(System.currentTimeMillis())

    /**
     * Occurrences déjà lues, pour les pages des grilles.
     *
     * Une page se construit sur le fil principal: elle ne peut pas interroger la base. Les
     * grilles puisent donc dans ce cache, rechargé dès qu'on approche de ses bords.
     */
    private var cache: List<Occurrence> = emptyList()
    private var cacheFrom = 0L
    private var cacheTo = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agenda)
        findViewById<View>(R.id.rootLayout).padForSystemBars()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_today -> {
                    goToToday()
                    true
                }
                R.id.action_search -> {
                    SearchSheet.newInstance().show(supportFragmentManager, TAG_SEARCH_SHEET)
                    true
                }
                R.id.action_calendar_system -> {
                    showCalendarSystemSheet()
                    true
                }
                else -> false
            }
        }

        viewModeGroup = findViewById(R.id.viewModeGroup)
        timePager = findViewById(R.id.timePager)
        monthHeader = findViewById(R.id.monthHeader)
        monthList = findViewById(R.id.monthList)
        scheduleRecycler = findViewById(R.id.scheduleRecyclerView)
        scheduleEmpty = findViewById(R.id.scheduleEmpty)
        calendarView = findViewById(R.id.calendar)
        recyclerView = findViewById(R.id.eventsRecyclerView)
        summaryView = findViewById(R.id.textSummary)
        sectionLabel = findViewById(R.id.selectedDateLabel)
        sectionSubtitle = findViewById(R.id.selectedDateSubtitle)
        emptyState = findViewById(R.id.emptyState)
        addButton = findViewById(R.id.addEvent)

        scheduleAdapter = ScheduleAdapter(onClick = ::openEditor)
        scheduleRecycler.adapter = scheduleAdapter
        scheduleRecycler.addOnScrollListener(FabScrollBehaviour(addButton))

        eventAdapter = EventAdapter(onClick = { openEditor(it.occurrence) })
        recyclerView.adapter = eventAdapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        ItemTouchHelper(SwipeToDeleteCallback(this, ::onSwipeDelete))
            .attachToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(FabScrollBehaviour(addButton))

        calendarView.onDateSelected = { timestamp ->
            selectedDate = DateLabels.key(timestamp)
            renderSection()
            loadEvents()
        }
        calendarView.onMonthChanged = { loadMonth() }

        addButton.setOnClickListener { openEditor(null) }

        viewModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) applyViewMode(viewModeOf(checkedId), remember = true)
        }
        timePager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = onPagerMoved(position)
        })

        supportFragmentManager.setFragmentResultListener(EventEditSheet.REQUEST_KEY, this) { _, result ->
            onEditorResult(result)
        }
        supportFragmentManager.setFragmentResultListener(SearchSheet.REQUEST_KEY, this) { _, result ->
            goToDate(Zones.localDate(SearchSheet.selectedStart(result), Zones.current()))
        }

        applyCalendarSystem(CalendarSystems.current(this))
        viewMode = AgendaSettings.view(this)
        viewModeGroup.check(buttonOf(viewMode))
        applyViewMode(viewMode, remember = false)
    }

    override fun onResume() {
        super.onResume()
        // Un événement a pu être supprimé ailleurs, ou la journée avoir changé.
        refresh()
        // Filet: si l'alarme unique a été perdue — permission retirée puis rendue, arrêt forcé
        // de l'application — l'ouverture de l'agenda la remet en place.
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { EventAlarmScheduler.rearm(applicationContext) }
        }
    }

    private fun refresh() {
        when (viewMode) {
            AgendaView.MONTH -> {
                loadEvents()
                loadMonth()
            }
            AgendaView.SCHEDULE -> loadSchedule()
            else -> reloadPages()
        }
    }

    // ----- Découpage -----

    private fun applyViewMode(mode: AgendaView, remember: Boolean) {
        viewMode = mode
        if (remember) AgendaSettings.saveView(this, mode)

        val isMonth = mode == AgendaView.MONTH
        val isSchedule = mode == AgendaView.SCHEDULE
        // L'en-tête vit dans la barre d'application, la liste dans le contenu: les deux se
        // montrent et se cachent ensemble.
        monthHeader.visibility = if (isMonth) View.VISIBLE else View.GONE
        monthList.visibility = if (isMonth) View.VISIBLE else View.GONE
        scheduleRecycler.visibility = if (isSchedule) View.VISIBLE else View.GONE
        timePager.visibility = if (isMonth || isSchedule) View.GONE else View.VISIBLE
        if (!isSchedule) scheduleEmpty.visibility = View.GONE

        if (isSchedule) {
            pagerAdapter = null
            timePager.adapter = null
            loadSchedule()
            return
        }

        if (isMonth) {
            pagerAdapter = null
            timePager.adapter = null
            // La grille du mois se cale sur le jour qu'on regardait: revenir au mois après avoir
            // parcouru des semaines ne doit pas ramener au point de départ.
            calendarView.selectDate(EventTimes.dayStart(selectedDate))
            loadEvents()
            loadMonth()
            renderSection()
            return
        }

        // L'adaptateur porte le nombre de jours par page: changer de découpage, c'est en changer.
        val adapter = AgendaPagerAdapter(
            daysPerPage = mode.daysPerPage,
            occurrencesOf = ::occurrencesFor,
            onSlotClick = ::openEditorAt,
            onOccurrenceClick = ::openEditor
        )
        pagerAdapter = adapter
        timePager.adapter = adapter
        timePager.setCurrentItem(adapter.positionOf(currentDate()), false)
        onPagerMoved(timePager.currentItem)
    }

    private fun viewModeOf(buttonId: Int): AgendaView = when (buttonId) {
        R.id.btnViewDay -> AgendaView.DAY
        R.id.btnViewWeek -> AgendaView.WEEK
        R.id.btnViewSchedule -> AgendaView.SCHEDULE
        else -> AgendaView.MONTH
    }

    private fun buttonOf(mode: AgendaView): Int = when (mode) {
        AgendaView.DAY -> R.id.btnViewDay
        AgendaView.WEEK -> R.id.btnViewWeek
        AgendaView.MONTH -> R.id.btnViewMonth
        AgendaView.SCHEDULE -> R.id.btnViewSchedule
    }

    private fun currentDate(): LocalDate =
        Zones.localDate(EventTimes.dayStart(selectedDate), Zones.current())

    private fun goToToday() = goToDate(LocalDate.now())

    /** Amène le découpage courant sur [date], quel qu'il soit. */
    private fun goToDate(date: LocalDate) {
        selectedDate = date.toString()
        when (viewMode) {
            AgendaView.MONTH -> calendarView.selectDate(EventTimes.dayStart(selectedDate), notify = true)
            AgendaView.SCHEDULE -> loadSchedule()
            else -> pagerAdapter?.let { timePager.setCurrentItem(it.positionOf(date), true) }
        }
    }

    /**
     * Planning: les trois prochains mois d'un seul tenant.
     *
     * La liste part du jour même et non du passé: c'est la vue du « qu'est-ce qui vient
     * ensuite », et ce qui est derrière se retrouve par la recherche.
     */
    private fun loadSchedule() {
        val zone = Zones.current()
        val from = Zones.dayStart(Zones.localDate(EventTimes.dayStart(selectedDate), zone), zone)
        val to = from + SCHEDULE_SPAN_MS
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { repository.occurrences(from, to, zone) }
            scheduleAdapter.submitList(ScheduleAdapter.itemsOf(found, zone))
            scheduleEmpty.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
            summaryView.text = when (found.size) {
                0 -> getString(R.string.calendar_summary_none)
                1 -> getString(R.string.calendar_summary_one)
                else -> getString(R.string.calendar_summary_other, found.size)
            }
        }
    }

    // ----- Grilles -----

    private fun onPagerMoved(position: Int) {
        val adapter = pagerAdapter ?: return
        val days = adapter.daysAt(position)
        selectedDate = referenceDay(days).toString()

        val zone = Zones.current()
        val from = Zones.dayStart(days.first(), zone)
        val to = Zones.dayEnd(days.last(), zone)
        renderRangeSummary(days)
        ensureRange(from, to)
    }

    /**
     * Jour de référence dans la page affichée.
     *
     * Une semaine ne sélectionne pas son lundi: elle contient le jour qu'on regardait. Prendre le
     * premier jour de la page ferait retomber sur le lundi à chaque passage par la vue semaine,
     * et la vue jour s'ouvrirait là plutôt que sur le jour choisi.
     *
     * Quand on balaie vers une autre période, le jour de la semaine est conservé — regarder un
     * vendredi puis avancer d'une semaine amène au vendredi suivant, pas à son lundi.
     */
    private fun referenceDay(days: List<LocalDate>): LocalDate {
        val current = currentDate()
        if (current in days) return current
        return days.firstOrNull { it.dayOfWeek == current.dayOfWeek } ?: days.first()
    }

    /**
     * Garantit que le cache couvre `[from, to]`, en le débordant largement.
     *
     * Recharger à chaque balayage coûterait une requête par page: la marge d'un mois de chaque
     * côté fait qu'on ne relit qu'en arrivant vraiment ailleurs.
     */
    private fun ensureRange(from: Long, to: Long, force: Boolean = false) {
        if (!force && from >= cacheFrom && to <= cacheTo) return
        val paddedFrom = from - CACHE_PADDING_MS
        val paddedTo = to + CACHE_PADDING_MS
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                repository.occurrences(paddedFrom, paddedTo)
            }
            cache = loaded
            cacheFrom = paddedFrom
            cacheTo = paddedTo
            pagerAdapter?.notifyDataSetChanged()
        }
    }

    private fun reloadPages() {
        val adapter = pagerAdapter ?: return
        val days = adapter.daysAt(timePager.currentItem)
        val zone = Zones.current()
        ensureRange(
            Zones.dayStart(days.first(), zone),
            Zones.dayEnd(days.last(), zone),
            force = true
        )
    }

    /** Filtre du cache: appelé pendant la construction d'une page, donc sans accès à la base. */
    private fun occurrencesFor(days: List<LocalDate>): List<Occurrence> {
        if (days.isEmpty()) return emptyList()
        val zone = Zones.current()
        val from = Zones.dayStart(days.first(), zone)
        val to = Zones.dayEnd(days.last(), zone)
        return cache.filter { it.overlaps(from, to) }
    }

    private fun renderRangeSummary(days: List<LocalDate>) {
        summaryView.text = if (days.size == 1) {
            DateLabels.weekday(noon(days.first())).replaceFirstChar { it.titlecase(Locale.FRANCE) }
        } else {
            getString(
                R.string.agenda_range,
                DateLabels.absoluteDay(noon(days.first())),
                DateLabels.absoluteDay(noon(days.last()))
            )
        }
    }

    /** Midi: jamais ambigu, quelle que soit la nuit du changement d'heure. */
    private fun noon(date: LocalDate): Long =
        Zones.toUtc(date.atTime(12, 0), Zones.current())

    // ----- Mode mois -----

    private fun loadEvents() {
        val date = selectedDate
        val (dayStart, dayEnd) = EventTimes.dayRange(date)
        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                val withReminder = repository.eventsWithReminder()
                repository.occurrences(dayStart, dayEnd)
                    .map { EventRow(it, withReminder.contains(it.eventId)) }
            }
            // Une réponse tardive ne doit pas écraser la journée que l'utilisateur vient d'ouvrir.
            if (date != selectedDate) return@launch
            eventAdapter.submitList(rows) { renderEmptyState(rows.isEmpty()) }
        }
    }

    /** Recharge les pastilles de la plage visible et le décompte du mois affiché. */
    private fun loadMonth() {
        val (visibleStart, visibleEnd) = calendarView.visibleRangeUtc()
        val (monthStart, monthEnd) = calendarView.monthRangeUtc()
        lifecycleScope.launch {
            val (marked, monthCount) = withContext(Dispatchers.IO) {
                repository.dayColors(visibleStart, visibleEnd) to
                        repository.countIn(monthStart, monthEnd)
            }
            calendarView.setMarkedDates(marked)
            summaryView.text = when (monthCount) {
                0 -> getString(R.string.calendar_summary_none)
                1 -> getString(R.string.calendar_summary_one)
                else -> getString(R.string.calendar_summary_other, monthCount)
            }
        }
    }

    private fun renderSection() {
        val timestamp = EventTimes.dayStart(selectedDate)
        val system = calendarView.calendarSystem
        val weekday = CalendarSystems.dayTitle(this, system, timestamp)
        val isToday = DateLabels.isToday(timestamp)
        sectionLabel.text = if (isToday) {
            getString(R.string.calendar_section_today, weekday)
        } else {
            getString(R.string.calendar_section_other, weekday)
                .replaceFirstChar { it.titlecase(Locale.FRANCE) }
        }

        val gregorian = CalendarSystems.daySubtitle(system, timestamp)
        sectionSubtitle.text = gregorian.orEmpty()
        sectionSubtitle.visibility = if (gregorian == null) View.GONE else View.VISIBLE
    }

    private fun renderEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /**
     * Choix du système calendaire. Feuille légère, sur le modèle du sélecteur de couleur de
     * l'éditeur de notes: deux lignes, une coche sur l'option active.
     */
    private fun showCalendarSystemSheet() {
        val view = LayoutInflater.from(this).inflate(R.layout.sheet_calendar_system, null)
        val row = view.findViewById<LinearLayout>(R.id.systemRow)
        val sheet = BottomSheetDialog(this)
        val active = CalendarSystems.current(this)

        for (system in CalendarSystem.values()) {
            val item = LayoutInflater.from(this).inflate(R.layout.item_calendar_system, row, false)
            item.findViewById<TextView>(R.id.label).setText(system.labelRes)
            item.findViewById<ImageView>(R.id.check).visibility =
                if (system == active) View.VISIBLE else View.GONE
            item.contentDescription = getString(system.labelRes)
            item.setOnClickListener {
                CalendarSystems.save(this, system)
                applyCalendarSystem(system)
                loadMonth()
                sheet.dismiss()
            }
            row.addView(item)
        }

        sheet.setContentView(view)
        sheet.show()
    }

    private fun applyCalendarSystem(system: CalendarSystem) {
        calendarView.calendarSystem = system
        renderSection()
    }

    // ----- Édition -----

    /** Création à un créneau tapoté dans une grille: l'heure est déjà décidée. */
    private fun openEditorAt(moment: LocalDateTime) {
        val startUtc = Zones.toUtc(moment, Zones.current())
        EventEditSheet
            .newInstance(null, 0L, emptyList(), DateLabels.key(startUtc), startUtc)
            .show(supportFragmentManager, TAG_EDIT_SHEET)
    }

    private fun openEditor(occurrence: Occurrence?) {
        if (occurrence == null) {
            EventEditSheet.newInstance(null, 0L, emptyList(), selectedDate)
                .show(supportFragmentManager, TAG_EDIT_SHEET)
            return
        }
        // L'éditeur a besoin de la ligne complète — couleur, lieu, règle — que l'occurrence
        // affichée ne porte pas: elle n'en garde que ce que la grille montre.
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val event = repository.byId(occurrence.eventId) ?: return@withContext null
                event to repository.remindersOf(event.id)
            } ?: return@launch
            EventEditSheet
                .newInstance(loaded.first, occurrence.occurrenceStartUtc, loaded.second, selectedDate)
                .show(supportFragmentManager, TAG_EDIT_SHEET)
        }
    }

    private fun onEditorResult(result: Bundle) {
        val eventId = EventEditSheet.targetEventId(result)
        val occurrenceStart = EventEditSheet.targetOccurrenceStart(result)
        val isDelete = EventEditSheet.action(result) == EventEditSheet.ACTION_DELETE

        if (eventId == null) {
            saveDraft(EventEditSheet.draft(result), null, 0L, EditScope.WHOLE_SERIES)
            return
        }
        // La portée n'est demandée que lorsqu'elle se pose: un événement unique n'en a qu'une, et
        // poser la question quand même ferait payer à tout le monde le prix des séries.
        if (!EventEditSheet.targetIsSeries(result)) {
            applyEdit(result, eventId, occurrenceStart, isDelete, EditScope.WHOLE_SERIES)
            return
        }
        EditScopeSheet.show(this, isDelete) { scope ->
            applyEdit(result, eventId, occurrenceStart, isDelete, scope)
        }
    }

    private fun applyEdit(
        result: Bundle,
        eventId: String,
        occurrenceStart: Long,
        isDelete: Boolean,
        scope: EditScope
    ) {
        if (isDelete) {
            deleteOccurrence(eventId, occurrenceStart, scope)
        } else {
            saveDraft(EventEditSheet.draft(result), eventId, occurrenceStart, scope)
        }
    }

    private fun saveDraft(
        draft: EventDraft,
        eventId: String?,
        occurrenceStart: Long,
        scope: EditScope
    ) {
        val message = if (eventId == null) {
            getString(R.string.event_created, DateLabels.time(draft.startUtc))
        } else {
            getString(R.string.event_updated)
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (eventId == null) {
                    repository.create(draft)
                } else {
                    repository.update(eventId, occurrenceStart, draft, scope)
                }
                // Une seule alarme sert tous les rappels de l'agenda: rien à annuler ni à poser
                // événement par événement, il suffit de la recalculer.
                EventAlarmScheduler.rearm(applicationContext)
            }
            refresh()
            snackbar(message).show()
        }
    }

    private fun onSwipeDelete(position: Int) {
        val occurrence = eventAdapter.currentList.getOrNull(position)?.occurrence ?: return
        // Un balayage ne peut pas poser de question: sur une série, il ne retire que l'occurrence
        // balayée, ce qui est à la fois le geste le plus attendu et le plus facile à annuler.
        val scope = if (occurrence.isRecurring) EditScope.THIS_ONE else EditScope.WHOLE_SERIES
        deleteOccurrence(occurrence.eventId, occurrence.occurrenceStartUtc, scope)
    }

    private fun deleteOccurrence(eventId: String, occurrenceStart: Long, scope: EditScope) {
        lifecycleScope.launch {
            // L'événement et ses rappels sont relus avant l'effacement: c'est ce qui permet au
            // « Annuler » de les remettre tels quels, la suppression en cascade étant définitive.
            val removed = withContext(Dispatchers.IO) {
                val event = repository.byId(eventId) ?: return@withContext null
                val reminders = repository.remindersOf(eventId)
                val byException = repository.delete(eventId, occurrenceStart, scope)
                EventAlarmScheduler.rearm(applicationContext)
                Triple(event, reminders, byException)
            } ?: return@launch

            refresh()
            snackbar(getString(R.string.event_deleted))
                .setAction(R.string.action_undo) {
                    if (removed.third) {
                        restoreOccurrence(eventId, occurrenceStart)
                    } else {
                        restore(removed.first, removed.second)
                    }
                }
                .show()
        }
    }

    private fun restore(event: Event, reminders: List<Int>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.restore(event, reminders)
                EventAlarmScheduler.rearm(applicationContext)
            }
            refresh()
        }
    }

    private fun restoreOccurrence(eventId: String, occurrenceStart: Long) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.restoreOccurrence(eventId, occurrenceStart)
                EventAlarmScheduler.rearm(applicationContext)
            }
            refresh()
        }
    }

    private fun snackbar(message: String): Snackbar =
        Snackbar.make(findViewById(R.id.rootLayout), message, Snackbar.LENGTH_LONG)
            .setAnchorView(addButton)

    private companion object {
        const val TAG_EDIT_SHEET = "event_edit_sheet"
        const val TAG_SEARCH_SHEET = "agenda_search_sheet"

        /** Profondeur du planning: trois mois, assez pour couvrir un trimestre d'un seul tenant. */
        const val SCHEDULE_SPAN_MS = 90 * 86_400_000L

        /** Marge de chargement de part et d'autre de la période affichée. */
        const val CACHE_PADDING_MS = 30 * 86_400_000L
    }
}
