package com.majdus.organisateur

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.EventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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

    // Le compteur passe par le dépôt et non par le DAO: une série ne compte qu'une ligne en base
    // mais peut avoir une occurrence aujourd'hui, ce que seule l'expansion sait dire.
    private val events by lazy { EventRepository(db) }

    private lateinit var tilesView: RecyclerView
    private lateinit var greetingView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var homeAdapter: HomeAdapter

    private var firstLoad = true

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val createBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) writeBackup(uri)
        }

    // Tous types acceptés: selon le gestionnaire de fichiers, un .json remonte comme du texte ou
    // comme un binaire quelconque. C'est la lecture qui dira si c'est bien une sauvegarde.
    private val openBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) readBackup(uri)
        }

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

        findViewById<View>(R.id.buttonMore).setOnClickListener(::showMoreMenu)

        renderHeader()
        askForPermissions()

        // L'agenda ne pose qu'une alarme, réarmée en cascade. Un arrêt forcé de l'application la
        // perd sans que rien ne la remette: l'accueil étant le point d'entrée, c'est ici qu'elle
        // se rattrape le plus tôt.
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { EventAlarmScheduler.rearm(applicationContext) }
        }
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
        val (dayStart, dayEnd) = EventTimes.dayRange(System.currentTimeMillis())
        return Counts(
            pendingTasks = db.taskDao().countPending(),
            notes = db.noteDao().countNotes(),
            activeAlarms = AlarmRepository(this).load().count { it.enabled },
            todayEvents = events.countIn(dayStart, dayEnd)
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
            else -> Agenda::class.java
        }
        startActivity(Intent(this, target))
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            inflate(R.menu.home)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_backup -> {
                        val day = BACKUP_DAY_FORMAT.format(Date())
                        createBackup.launch(getString(R.string.backup_file_name, day))
                        true
                    }
                    R.id.action_restore -> {
                        openBackup.launch(arrayOf("*/*"))
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    /**
     * L'écriture va à son terme même si l'écran est détruit entre-temps: un fichier à moitié
     * écrit serait pire qu'aucun. En cas d'échec, le fichier créé par le sélecteur est retiré pour
     * ne pas laisser traîner une sauvegarde vide qu'on croirait bonne.
     */
    private fun writeBackup(uri: Uri) {
        val context = applicationContext
        lifecycleScope.launch {
            val saved = withContext(NonCancellable + Dispatchers.IO) {
                val result = runCatching {
                    val output = context.contentResolver.openOutputStream(uri, "w")
                        ?: error("flux indisponible")
                    output.use { Backup.export(context, it) }
                }
                if (result.isFailure) {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
                result.isSuccess
            }
            showSnackbar(if (saved) R.string.backup_saved else R.string.backup_save_failed)
        }
    }

    /** Le fichier est lu et vérifié en entier avant la question: rien n'est touché s'il est mauvais. */
    private fun readBackup(uri: Uri) {
        val context = applicationContext
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw Backup.InvalidBackupException("flux indisponible")
                    input.use { Backup.read(it) }
                }
            }
            result.fold(
                onSuccess = ::confirmRestore,
                onFailure = { error ->
                    showSnackbar(
                        if (error is Backup.TooRecentBackupException) R.string.backup_too_recent
                        else R.string.backup_invalid
                    )
                }
            )
        }
    }

    private fun confirmRestore(content: Backup.Content) {
        val counts = listOf(
            resources.getQuantityString(R.plurals.backup_tasks, content.tasks.size, content.tasks.size),
            resources.getQuantityString(R.plurals.backup_notes, content.notes.size, content.notes.size),
            resources.getQuantityString(R.plurals.backup_alarms, content.alarms.size, content.alarms.size),
            resources.getQuantityString(R.plurals.backup_events, content.eventCount, content.eventCount)
        ).joinToString(" · ")
        val summary = if (content.exportedAt > 0) {
            getString(R.string.backup_restore_dated, BACKUP_DATE_FORMAT.format(Date(content.exportedAt))) +
                "\n" + counts
        } else {
            counts
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.backup_restore_title)
            .setMessage(getString(R.string.backup_restore_message, summary))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.backup_restore_confirm) { _, _ -> restore(content) }
            .show()
    }

    /** Comme l'écriture, la restauration ne s'interrompt pas avec l'écran. */
    private fun restore(content: Backup.Content) {
        val context = applicationContext
        lifecycleScope.launch {
            val restored = withContext(NonCancellable + Dispatchers.IO) {
                runCatching { Backup.restore(context, content) }.isSuccess
            }
            refreshTiles()
            showSnackbar(if (restored) R.string.backup_restored else R.string.backup_restore_failed)
        }
    }

    private fun showSnackbar(messageRes: Int) {
        Snackbar.make(findViewById(R.id.rootLayout), messageRes, Snackbar.LENGTH_LONG).show()
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
        private val BACKUP_DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE)
        private val BACKUP_DATE_FORMAT = SimpleDateFormat("d MMMM yyyy 'à' HH:mm", Locale.FRANCE)
    }
}
