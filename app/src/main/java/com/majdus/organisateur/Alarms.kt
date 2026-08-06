package com.majdus.organisateur

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar

/**
 * Écran des rappels quotidiens.
 *
 * L'activité ne fait qu'orchestrer: [AlarmRepository] détient l'état, [AlarmScheduler] la
 * planification système, [AlarmEditSheet] la saisie.
 */
class Alarms : AppCompatActivity() {

    private val repository by lazy { AlarmRepository(this) }
    private val ticker = Handler(Looper.getMainLooper())

    private lateinit var recyclerView: RecyclerView
    private lateinit var summaryView: TextView
    private lateinit var emptyState: View
    private lateinit var addButton: ExtendedFloatingActionButton
    private lateinit var alarmAdapter: AlarmAdapter

    private var firstLoad = true

    /** Rafraîchit les décomptes ("dans 8 h 20") tant que l'écran est visible. */
    private val countdownTick = object : Runnable {
        override fun run() {
            refreshCountdowns()
            ticker.postDelayed(this, COUNTDOWN_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarms)
        findViewById<View>(R.id.rootLayout).padForSystemBars()

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        summaryView = findViewById(R.id.textSummary)
        emptyState = findViewById(R.id.emptyState)
        addButton = findViewById(R.id.addAlarm)
        recyclerView = findViewById(R.id.alarms)

        alarmAdapter = AlarmAdapter(onToggle = ::onToggleAlarm, onClick = ::openEditor)
        recyclerView.adapter = alarmAdapter
        // Le basculement d'un interrupteur ne doit pas provoquer de fondu sur la carte.
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        ItemTouchHelper(SwipeToDeleteCallback(this, ::onSwipeDelete))
            .attachToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(FabScrollBehaviour(addButton))

        addButton.setOnClickListener { openEditor(null) }
        findViewById<MaterialButton>(R.id.emptyAction).setOnClickListener { openEditor(null) }

        supportFragmentManager.setFragmentResultListener(AlarmEditSheet.REQUEST_KEY, this) { _, result ->
            onEditorResult(result)
        }

        // Répare l'existant: normalise le format hérité, réarme les rappels actifs et
        // annule les réveils devenus orphelins.
        repository.migrateLegacyEntries()
        AlarmScheduler.rescheduleAll(this)
    }

    override fun onResume() {
        super.onResume()
        // Un rappel a pu sonner pendant que l'écran était en arrière-plan.
        refresh()
    }

    override fun onStart() {
        super.onStart()
        ticker.postDelayed(countdownTick, COUNTDOWN_INTERVAL_MS)
    }

    override fun onStop() {
        super.onStop()
        ticker.removeCallbacks(countdownTick)
    }

    private fun refresh() {
        val alarms = repository.load()
        alarmAdapter.submitList(alarms) { renderEmptyState(alarms.isEmpty()) }
        summaryView.text = AlarmFormatter.summary(this, alarms)
        if (firstLoad && alarms.isNotEmpty()) {
            firstLoad = false
            recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_slide_up)
            recyclerView.scheduleLayoutAnimation()
        }
    }

    private fun refreshCountdowns() {
        summaryView.text = AlarmFormatter.summary(this, alarmAdapter.currentList)
        if (alarmAdapter.itemCount > 0) {
            alarmAdapter.notifyItemRangeChanged(
                0,
                alarmAdapter.itemCount,
                AlarmAdapter.PAYLOAD_COUNTDOWN
            )
        }
    }

    private fun renderEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        // L'état vide porte déjà son propre appel à l'action.
        if (isEmpty) addButton.hide() else addButton.show()
    }

    private fun openEditor(alarm: Alarm?) {
        AlarmEditSheet.newInstance(alarm).show(supportFragmentManager, TAG_EDIT_SHEET)
    }

    private fun onEditorResult(result: Bundle) {
        val alarm = AlarmEditSheet.editedAlarm(result) ?: return
        val original = AlarmEditSheet.originalAlarm(result)
        when (AlarmEditSheet.action(result)) {
            AlarmEditSheet.ACTION_DELETE -> {
                repository.delete(alarm)
                refresh()
                showUndoDeleteSnackbar(alarm)
            }
            AlarmEditSheet.ACTION_SAVE -> {
                if (original == null) {
                    if (!repository.add(alarm)) {
                        showSnackbar(getString(R.string.alarm_already_exists))
                        return
                    }
                    refresh()
                    showSnackbar(getString(R.string.alarm_created, AlarmFormatter.whenLabel(this, alarm)))
                } else {
                    repository.update(original, alarm)
                    refresh()
                    showSnackbar(getString(R.string.alarm_updated, AlarmFormatter.whenLabel(this, alarm)))
                }
            }
        }
    }

    private fun onToggleAlarm(alarm: Alarm, enabled: Boolean) {
        repository.setEnabled(alarm, enabled)
        refresh()
        val message = if (enabled) {
            val rescheduled = alarm.copy(enabled = true)
            getString(R.string.alarm_enabled_toast, AlarmFormatter.whenLabel(this, rescheduled))
        } else {
            getString(R.string.alarm_disabled_toast)
        }
        showSnackbar(message)
    }

    private fun onSwipeDelete(position: Int) {
        val alarm = alarmAdapter.currentList.getOrNull(position) ?: return
        repository.delete(alarm)
        refresh()
        showUndoDeleteSnackbar(alarm)
    }

    private fun showUndoDeleteSnackbar(alarm: Alarm) {
        snackbar(getString(R.string.alarm_deleted))
            .setAction(R.string.alarm_undo) {
                repository.add(alarm)
                refresh()
            }
            .show()
    }

    private fun showSnackbar(message: String) {
        snackbar(message).show()
    }

    private fun snackbar(message: String): Snackbar =
        Snackbar.make(findViewById(R.id.rootLayout), message, Snackbar.LENGTH_LONG)
            .setAnchorView(if (addButton.isShown) addButton else null)

    companion object {
        private const val TAG_EDIT_SHEET = "alarm_edit_sheet"
        private const val COUNTDOWN_INTERVAL_MS = 30_000L
    }
}
