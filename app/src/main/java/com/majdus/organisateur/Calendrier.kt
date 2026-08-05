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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Écran du calendrier: un mois navigable au-dessus de l'agenda du jour sélectionné.
 *
 * Le mois porte une pastille sur chaque jour occupé, ce qui évite d'avoir à ouvrir les
 * journées une par une pour savoir où sont les événements.
 */
class Calendrier : AppCompatActivity() {

    private val db by lazy { AppDatabase.getDatabase(this) }

    private lateinit var calendarView: MonthCalendarView
    private lateinit var recyclerView: RecyclerView
    private lateinit var summaryView: TextView
    private lateinit var sectionLabel: TextView
    private lateinit var sectionSubtitle: TextView
    private lateinit var todayAction: MaterialButton
    private lateinit var emptyState: View
    private lateinit var addButton: ExtendedFloatingActionButton
    private lateinit var eventAdapter: EventAdapter

    /** Jour affiché dans l'agenda, au format de stockage "yyyy-MM-dd". */
    private var selectedDate: String = DateLabels.key(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendrier)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_calendar_system -> {
                    showCalendarSystemSheet()
                    true
                }
                else -> false
            }
        }

        calendarView = findViewById(R.id.calendar)
        recyclerView = findViewById(R.id.eventsRecyclerView)
        summaryView = findViewById(R.id.textSummary)
        sectionLabel = findViewById(R.id.selectedDateLabel)
        sectionSubtitle = findViewById(R.id.selectedDateSubtitle)
        todayAction = findViewById(R.id.todayAction)
        emptyState = findViewById(R.id.emptyState)
        addButton = findViewById(R.id.addEvent)

        eventAdapter = EventAdapter(onClick = ::openEditor)
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
        // Changer de mois ne change pas le jour sélectionné, mais bien la plage à marquer.
        calendarView.onMonthChanged = { loadMonth() }

        todayAction.setOnClickListener {
            calendarView.selectDate(System.currentTimeMillis(), notify = true)
        }
        addButton.setOnClickListener { openEditor(null) }

        supportFragmentManager.setFragmentResultListener(EventEditSheet.REQUEST_KEY, this) { _, result ->
            onEditorResult(result)
        }

        // Le choix enregistré est restitué une fois les callbacks branchés: poser le système
        // redessine la grille, et l'en-tête du jour doit suivre dans la foulée.
        applyCalendarSystem(CalendarSystems.current(this))
    }

    override fun onResume() {
        super.onResume()
        // Un événement a pu être supprimé ailleurs, ou la journée avoir changé.
        refresh()
    }

    private fun refresh() {
        loadEvents()
        loadMonth()
    }

    private fun loadEvents() {
        val date = selectedDate
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) { db.eventDao().getEventsByDate(date) }
            // Une réponse tardive ne doit pas écraser la journée que l'utilisateur vient d'ouvrir.
            if (date != selectedDate) return@launch
            eventAdapter.submitList(events) { renderEmptyState(events.isEmpty()) }
        }
    }

    /** Recharge les pastilles de la plage visible et le décompte du mois affiché. */
    private fun loadMonth() {
        val (visibleStart, visibleEnd) = calendarView.visibleRange()
        val (monthStart, monthEnd) = calendarView.monthRange()
        lifecycleScope.launch {
            val marked = withContext(Dispatchers.IO) {
                db.eventDao().getDatesBetween(visibleStart, visibleEnd).toSet()
            }
            val monthCount = withContext(Dispatchers.IO) {
                db.eventDao().countBetween(monthStart, monthEnd)
            }
            calendarView.setMarkedDates(marked)
            summaryView.text = when (monthCount) {
                0 -> getString(R.string.calendar_summary_none)
                1 -> getString(R.string.calendar_summary_one)
                else -> getString(R.string.calendar_summary_other, monthCount)
            }
        }
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
                // Les bornes du mois affiché ont changé, donc le décompte du résumé aussi.
                loadMonth()
                sheet.dismiss()
            }
            row.addView(item)
        }

        sheet.setContentView(view)
        sheet.show()
    }

    /**
     * Pose le système sur la grille et remet l'en-tête du jour en accord avec elle.
     *
     * Le rechargement du mois est laissé à l'appelant: au démarrage, `onResume` s'en charge déjà
     * — l'y refaire ici coûterait une requête pour rien.
     */
    private fun applyCalendarSystem(system: CalendarSystem) {
        calendarView.calendarSystem = system
        renderSection()
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
        // Le raccourci de retour au jour même n'a de sens qu'ailleurs que sur lui.
        todayAction.visibility = if (isToday) View.GONE else View.VISIBLE
    }

    private fun renderEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun openEditor(event: Event?) {
        EventEditSheet.newInstance(event, selectedDate).show(supportFragmentManager, TAG_EDIT_SHEET)
    }

    private fun onEditorResult(result: Bundle) {
        val event = EventEditSheet.editedEvent(result) ?: return
        when (EventEditSheet.action(result)) {
            EventEditSheet.ACTION_DELETE -> deleteEvent(event)
            EventEditSheet.ACTION_SAVE -> {
                val original = EventEditSheet.originalEvent(result)
                val message = if (original == null) {
                    getString(
                        R.string.event_created,
                        String.format(Locale.FRANCE, "%02d:%02d", event.hour, event.minute)
                    )
                } else {
                    getString(R.string.event_updated)
                }
                write(message) {
                    if (original == null) {
                        db.eventDao().insert(event)
                    } else {
                        // L'ancienne notification est annulée explicitement: changer l'heure
                        // change l'instant de déclenchement, pas le PendingIntent.
                        EventScheduler.cancel(this@Calendrier, original)
                        db.eventDao().update(event)
                    }
                    EventScheduler.schedule(this@Calendrier, event)
                }
            }
        }
    }

    private fun onSwipeDelete(position: Int) {
        val event = eventAdapter.currentList.getOrNull(position) ?: return
        deleteEvent(event)
    }

    private fun deleteEvent(event: Event) {
        write(message = null) {
            EventScheduler.cancel(this@Calendrier, event)
            db.eventDao().delete(event)
        }
        snackbar(getString(R.string.event_deleted))
            .setAction(R.string.action_undo) {
                write(message = null) {
                    db.eventDao().insert(event)
                    EventScheduler.schedule(this@Calendrier, event)
                }
            }
            .show()
    }

    /** Écrit en base puis rafraîchit l'agenda; [message] est annoncé une fois la liste à jour. */
    private fun write(message: String?, block: suspend () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { block() }
            refresh()
            message?.let { snackbar(it).show() }
        }
    }

    private fun snackbar(message: String): Snackbar =
        Snackbar.make(findViewById(R.id.rootLayout), message, Snackbar.LENGTH_LONG)
            .setAnchorView(addButton)

    companion object {
        private const val TAG_EDIT_SHEET = "event_edit_sheet"
    }
}
