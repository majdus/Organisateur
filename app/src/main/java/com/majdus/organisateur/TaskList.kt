package com.majdus.organisateur

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Écran des tâches.
 *
 * L'activité orchestre: la base Room détient l'état, [TaskEditSheet] la saisie. Toute
 * suppression passe par un Snackbar « Annuler » plutôt que par une boîte de confirmation —
 * une action réversible n'a pas besoin d'être confirmée à l'avance.
 *
 * L'ordre de la liste appartient à l'utilisateur: un appui long soulève une tâche et la repose
 * où il veut, pour ranger par importance. Les tâches terminées restent groupées en bas, et le
 * déplacement ne franchit pas cette frontière.
 */
class TaskList : AppCompatActivity() {

    private val db by lazy { AppDatabase.getDatabase(this) }

    private lateinit var recyclerView: RecyclerView
    private lateinit var summaryView: TextView
    private lateinit var emptyState: View
    private lateinit var addButton: ExtendedFloatingActionButton
    private lateinit var taskAdapter: TaskAdapter

    private var firstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)
        findViewById<View>(R.id.rootLayout).padForSystemBars()

        findViewById<MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        summaryView = findViewById(R.id.textSummary)
        emptyState = findViewById(R.id.emptyState)
        addButton = findViewById(R.id.addTask)
        recyclerView = findViewById(R.id.tasks)

        taskAdapter = TaskAdapter(onToggle = ::onToggleTask, onClick = ::openEditor)
        recyclerView.adapter = taskAdapter
        // Cocher une case ne doit pas provoquer de fondu sur la carte.
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        ItemTouchHelper(
            TaskReorderCallback(
                context = this,
                onSwiped = ::onSwipeDelete,
                onMoved = taskAdapter::moveItem,
                isSameGroup = ::isSameGroup,
                onDragStarted = { addButton.hide() },
                onDragFinished = ::onDragFinished
            )
        ).attachToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(FabScrollBehaviour(addButton))

        addButton.setOnClickListener { openEditor(null) }
        findViewById<MaterialButton>(R.id.emptyAction).setOnClickListener { openEditor(null) }

        supportFragmentManager.setFragmentResultListener(TaskEditSheet.REQUEST_KEY, this) { _, result ->
            onEditorResult(result)
        }

        refresh()
    }

    /**
     * Deux tâches appartiennent-elles au même groupe ? Une tâche restante ne se dépose pas parmi
     * les terminées: cette frontière relève de la case à cocher.
     */
    private fun isSameGroup(from: Int, to: Int): Boolean {
        val moved = taskAdapter.itemAt(from) ?: return false
        val target = taskAdapter.itemAt(to) ?: return false
        return moved.isCompleted == target.isCompleted
    }

    /**
     * Carte relâchée: l'ordre affiché devient l'ordre enregistré.
     *
     * L'écriture est retenue dans [orderWrite] et attendue par [refresh]: relire la base avant
     * que l'ordre y soit posé ferait ressauter la carte à son ancienne place.
     */
    private fun onDragFinished() {
        addButton.show()
        val orderedIds = taskAdapter.currentOrderIds()
        val dao = db.taskDao()
        orderWrite = backgroundOrderWrites.launch { dao.applyOrder(orderedIds) }
    }

    private fun refresh(after: () -> Unit = {}) {
        lifecycleScope.launch {
            orderWrite?.join()
            val tasks = withContext(Dispatchers.IO) { db.taskDao().getAllTasks() }
            taskAdapter.submit(tasks)
            renderEmptyState(tasks.isEmpty())
            summaryView.text = summaryOf(tasks)
            if (firstLoad && tasks.isNotEmpty()) {
                firstLoad = false
                recyclerView.layoutAnimation =
                    AnimationUtils.loadLayoutAnimation(this@TaskList, R.anim.layout_animation_slide_up)
                recyclerView.scheduleLayoutAnimation()
            }
            after()
        }
    }

    /** "3 à faire · 2 terminées" — l'état de la liste en une ligne. */
    private fun summaryOf(tasks: List<Task>): String {
        if (tasks.isEmpty()) return getString(R.string.tasks_summary_empty)
        val done = tasks.count { it.isCompleted }
        val todo = tasks.size - done
        if (todo == 0) return getString(R.string.tasks_summary_all_done)
        val todoLabel = when (todo) {
            1 -> getString(R.string.tasks_todo_one)
            else -> getString(R.string.tasks_todo_other, todo)
        }
        val doneLabel = when (done) {
            0 -> getString(R.string.tasks_done_none)
            1 -> getString(R.string.tasks_done_one)
            else -> getString(R.string.tasks_done_other, done)
        }
        return getString(R.string.tasks_summary, todoLabel, doneLabel)
    }

    private fun renderEmptyState(isEmpty: Boolean) {
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        // L'état vide porte déjà son propre appel à l'action.
        if (isEmpty) addButton.hide() else addButton.show()
    }

    private fun openEditor(task: Task?) {
        TaskEditSheet.newInstance(task).show(supportFragmentManager, TAG_EDIT_SHEET)
    }

    private fun onEditorResult(result: Bundle) {
        val task = TaskEditSheet.editedTask(result) ?: return
        when (TaskEditSheet.action(result)) {
            TaskEditSheet.ACTION_DELETE -> deleteTask(task)
            TaskEditSheet.ACTION_SAVE -> {
                val isNew = TaskEditSheet.isNew(result)
                write(
                    message = getString(if (isNew) R.string.task_created else R.string.task_updated)
                ) {
                    val dao = db.taskDao()
                    if (isNew) {
                        // Une tâche qui vient d'être écrite se pose en fin de liste, comme le
                        // faisait le tri par date de création: on ajoute au bas de ce qu'on a
                        // déjà à faire, on ne s'impose pas en tête.
                        dao.insert(task.copy(position = (dao.maxPosition() ?: -1) + 1))
                    } else {
                        dao.update(task)
                    }
                }
            }
        }
    }

    /** Cocher est silencieux: le texte barré et le déplacement de la carte suffisent à le dire. */
    private fun onToggleTask(task: Task, completed: Boolean) {
        val updated = task.copy(isCompleted = completed)
        write(message = null) { db.taskDao().update(updated) }
    }

    private fun onSwipeDelete(position: Int) {
        val task = taskAdapter.itemAt(position) ?: return
        deleteTask(task)
    }

    private fun deleteTask(task: Task) {
        write(message = null) { db.taskDao().delete(task) }
        // L'annulation réinsère la tâche telle quelle, son rang compris: elle retrouve donc
        // exactement sa place dans la liste, et non le bas de la pile.
        snackbar(getString(R.string.task_deleted))
            .setAction(R.string.action_undo) {
                write(message = null) { db.taskDao().insert(task) }
            }
            .show()
    }

    /** Écrit en base puis rafraîchit la liste; [message] est annoncé une fois la liste à jour. */
    private fun write(message: String?, block: suspend () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { block() }
            refresh { message?.let(::showSnackbar) }
        }
    }

    private fun showSnackbar(message: String) {
        snackbar(message).show()
    }

    private fun snackbar(message: String): Snackbar =
        Snackbar.make(findViewById(R.id.rootLayout), message, Snackbar.LENGTH_LONG)
            .setAnchorView(if (addButton.isShown) addButton else null)

    companion object {
        private const val TAG_EDIT_SHEET = "task_edit_sheet"

        /**
         * Portée détachée du cycle de vie, comme pour les notes: déposer une carte puis quitter
         * l'écran aussitôt ne doit pas faire perdre le rangement.
         */
        private val backgroundOrderWrites = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Enregistrement de l'ordre encore en vol; toute relecture de la base l'attend. */
        @Volatile
        private var orderWrite: Job? = null
    }
}
