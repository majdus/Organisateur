package com.majdus.organisateur

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.majdus.organisateur.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Accueil: un point d'entrée par fonctionnalité, chacun affichant son propre état
 * (tâches restantes, rappels actifs, événements du jour) pour éviter d'avoir à ouvrir
 * un écran juste pour savoir s'il s'y passe quelque chose.
 */
class MainActivity : AppCompatActivity() {

    private val db by lazy { AppDatabase.getDatabase(this) }

    private lateinit var tilesView: RecyclerView
    private lateinit var greetingView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var homeAdapter: HomeAdapter

    private var firstLoad = true

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.rootLayout).padForSystemBars()

        greetingView = findViewById(R.id.textGreeting)
        subtitleView = findViewById(R.id.textSubtitle)
        tilesView = findViewById(R.id.tiles)

        homeAdapter = HomeAdapter(::openFeature)
        tilesView.adapter = homeAdapter
        tilesView.setHasFixedSize(true)

        renderHeader()
        askForPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Les compteurs peuvent avoir changé dans un autre écran — ou dans la nuit,
        // pour les événements du jour.
        renderHeader()
        refreshTiles()
    }

    private fun renderHeader() {
        val now = Calendar.getInstance()
        greetingView.setText(
            when (now.get(Calendar.HOUR_OF_DAY)) {
                in 5..11 -> R.string.greeting_morning
                in 12..17 -> R.string.greeting_afternoon
                else -> R.string.greeting_evening
            }
        )
        val today = DAY_FORMAT.format(now.time)
        subtitleView.text = getString(R.string.home_subtitle_prefix, today)
    }

    private fun refreshTiles() {
        lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) { loadCounts() }
            homeAdapter.submitList(tiles(counts))
            if (firstLoad) {
                firstLoad = false
                tilesView.layoutAnimation =
                    AnimationUtils.loadLayoutAnimation(this@MainActivity, R.anim.layout_animation_slide_up)
                tilesView.scheduleLayoutAnimation()
            }
        }
    }

    private suspend fun loadCounts(): Counts {
        val today = DATE_KEY_FORMAT.format(Date())
        return Counts(
            pendingTasks = db.taskDao().countPending(),
            notes = db.noteDao().countNotes(),
            activeAlarms = AlarmRepository(this).load().count { it.enabled },
            todayEvents = db.eventDao().countByDate(today)
        )
    }

    private fun tiles(counts: Counts): List<HomeTile> = listOf(
        HomeTile(
            id = HomeAdapter.TASKS,
            titleRes = R.string.title_tasks,
            subtitleRes = R.string.subtitle_tasks,
            iconRes = R.drawable.ic_tasks,
            accentRes = R.color.accent_tasks,
            accentSoftRes = R.color.accent_tasks_soft,
            count = quantity(
                counts.pendingTasks,
                R.string.home_tasks_none,
                R.string.home_tasks_one,
                R.string.home_tasks_other
            )
        ),
        HomeTile(
            id = HomeAdapter.NOTES,
            titleRes = R.string.title_notes,
            subtitleRes = R.string.subtitle_notes,
            iconRes = R.drawable.ic_notes,
            accentRes = R.color.accent_notes,
            accentSoftRes = R.color.accent_notes_soft,
            count = quantity(
                counts.notes,
                R.string.home_notes_none,
                R.string.home_notes_one,
                R.string.home_notes_other
            )
        ),
        HomeTile(
            id = HomeAdapter.ALARMS,
            titleRes = R.string.title_alarms,
            subtitleRes = R.string.subtitle_alarms,
            iconRes = R.drawable.ic_alarm_ring,
            accentRes = R.color.accent_alarms,
            accentSoftRes = R.color.accent_alarms_soft,
            count = quantity(
                counts.activeAlarms,
                R.string.home_alarms_none,
                R.string.home_alarms_one,
                R.string.home_alarms_other
            )
        ),
        HomeTile(
            id = HomeAdapter.CALENDAR,
            titleRes = R.string.title_calendar,
            subtitleRes = R.string.subtitle_calendar,
            iconRes = R.drawable.ic_calendar,
            accentRes = R.color.accent_calendar,
            accentSoftRes = R.color.accent_calendar_soft,
            count = quantity(
                counts.todayEvents,
                R.string.home_events_none,
                R.string.home_events_one,
                R.string.home_events_other
            )
        )
    )

    private fun quantity(value: Int, noneRes: Int, oneRes: Int, otherRes: Int): String = when (value) {
        0 -> getString(noneRes)
        1 -> getString(oneRes)
        else -> getString(otherRes, value)
    }

    private fun openFeature(tile: HomeTile) {
        val target = when (tile.id) {
            HomeAdapter.TASKS -> TaskList::class.java
            HomeAdapter.NOTES -> Notes::class.java
            HomeAdapter.ALARMS -> Alarms::class.java
            else -> Calendrier::class.java
        }
        startActivity(Intent(this, target))
    }

    private fun askForPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Le renvoi vers les réglages système n'est proposé qu'une fois: le faire à chaque
        // démarrage éjecte hors de l'application un utilisateur qui a déjà refusé.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val alreadyAsked = prefs.getBoolean(KEY_EXACT_ALARM_ASKED, false)
            if (!alarmManager.canScheduleExactAlarms() && !alreadyAsked) {
                prefs.edit().putBoolean(KEY_EXACT_ALARM_ASKED, true).apply()
                runCatching {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                }
            }
        }
    }

    private data class Counts(
        val pendingTasks: Int,
        val notes: Int,
        val activeAlarms: Int,
        val todayEvents: Int
    )

    companion object {
        private const val PREFS_NAME = "organisateur"
        private const val KEY_EXACT_ALARM_ASKED = "exact_alarm_asked"
        private val DAY_FORMAT = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)
        private val DATE_KEY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
    }
}
