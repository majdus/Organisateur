package com.majdus.organisateur

import android.os.Bundle
import android.widget.CalendarView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.Event
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Calendrier : AppCompatActivity() {
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var calendarView: CalendarView
    private lateinit var addEvent: FloatingActionButton
    private lateinit var selectedDateLabel: TextView
    private lateinit var eventAdapter: EventAdapter
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var eventsList = mutableListOf<Event>()
    private var currentDateString: String = ""

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
    private val displayFormat = SimpleDateFormat("dd MMMM yyyy", Locale.FRANCE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendrier)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        calendarView = findViewById(R.id.calendarView)
        eventsRecyclerView = findViewById(R.id.eventsRecyclerView)
        selectedDateLabel = findViewById(R.id.selectedDateLabel)
        addEvent = findViewById(R.id.addEvent)

        eventAdapter = EventAdapter(this, eventsList)
        eventsRecyclerView.adapter = eventAdapter

        // Initialize with today's date
        setCurrentDate(calendarView.date)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = java.util.Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            setCurrentDate(calendar.timeInMillis)
        }

        addEvent.setOnClickListener {
            val dialog = EventDialogFragment(this, null, currentDateString)
            dialog.show(supportFragmentManager, "Event")
        }
    }

    private fun setCurrentDate(timestamp: Long) {
        val date = Date(timestamp)
        currentDateString = dateFormat.format(date)
        selectedDateLabel.text = "Événements du ${displayFormat.format(date)}"
        loadEventsForDate(currentDateString)
    }

    private fun loadEventsForDate(dateString: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            eventsList = db.eventDao().getEventsByDate(dateString).toMutableList()
            withContext(Dispatchers.Main) {
                eventAdapter.updateEvents(eventsList)
            }
        }
    }

    fun addNewEvent(event: Event) {
        if (event.title.trim().isEmpty()) {
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            db.eventDao().insert(event)
            EventScheduler.schedule(this@Calendrier, event)
            withContext(Dispatchers.Main) {
                loadEventsForDate(currentDateString)
                Toast.makeText(this@Calendrier, "Événement ajouté", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateEvent(event: Event) {
        if (event.title.trim().isEmpty()) {
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            db.eventDao().update(event)
            EventScheduler.cancel(this@Calendrier, event)
            EventScheduler.schedule(this@Calendrier, event)
            withContext(Dispatchers.Main) {
                loadEventsForDate(currentDateString)
            }
        }
    }

    fun removeEvent(event: Event) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Supprimer cet événement ?")
        builder.setMessage(event.title)
        builder.setPositiveButton("Oui") { _, _ -> 
            lifecycleScope.launch(Dispatchers.IO) {
                db.eventDao().delete(event)
                EventScheduler.cancel(this@Calendrier, event)
                withContext(Dispatchers.Main) {
                    loadEventsForDate(currentDateString)
                    Toast.makeText(this@Calendrier, "Événement supprimé", Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.setNegativeButton("Non") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    fun editEvent(event: Event) {
        val dialog = EventDialogFragment(this, event, currentDateString)
        dialog.show(supportFragmentManager, "Event")
    }
}