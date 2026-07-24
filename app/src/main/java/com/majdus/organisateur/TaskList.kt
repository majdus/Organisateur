package com.majdus.organisateur

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.Task
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TaskList : AppCompatActivity() {
    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var addTask: android.widget.ImageButton
    private lateinit var taskAdapter: TaskAdapter
    private val db by lazy { AppDatabase.getDatabase(this) }
    private var tasksList = mutableListOf<Task>()

    private val removeItemMessage = "Supprimer cette tâche?"
    private val removedToast = "Tâche supprimée!"
    private val successToast = "Nouvelle tâche ajoutée!"
    private val updateToast = "Tâche mise à jour!"
    private val errorToast = "La tâche ne peut pas être vide. Saisissez un texte avant de valider!"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        tasksRecyclerView = findViewById(R.id.tasks)
        addTask = findViewById(R.id.addTask)
        addTask.setOnClickListener { 
            val dialog = TaskDialogFragment(this, null)
            dialog.show(supportFragmentManager, "Task")
        }

        taskAdapter = TaskAdapter(this, tasksList)
        tasksRecyclerView.adapter = taskAdapter

        loadTasks()
    }

    private fun loadTasks() {
        lifecycleScope.launch(Dispatchers.IO) {
            tasksList = db.taskDao().getAllTasks().toMutableList()
            withContext(Dispatchers.Main) {
                taskAdapter.updateTasks(tasksList)
            }
        }
    }

    fun addNewItem(task: Task) {
        if (task.title.trim().isEmpty()) {
            Toast.makeText(this, errorToast, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            db.taskDao().insert(task)
            withContext(Dispatchers.Main) {
                loadTasks()
                Toast.makeText(this@TaskList, successToast, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateItem(task: Task) {
        if (task.title.trim().isEmpty()) {
            Toast.makeText(this, errorToast, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            db.taskDao().update(task)
            withContext(Dispatchers.Main) {
                loadTasks()
                // Only show toast if it's not just a checkbox toggle to avoid spamming
            }
        }
    }

    fun removeItem(task: Task) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(removeItemMessage)
        builder.setMessage(task.title)
        builder.setPositiveButton("Oui") { _, _ -> 
            lifecycleScope.launch(Dispatchers.IO) {
                db.taskDao().delete(task)
                withContext(Dispatchers.Main) {
                    loadTasks()
                    Toast.makeText(this@TaskList, removedToast, Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.setNegativeButton("Non") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    fun editItem(task: Task) {
        val dialog = TaskDialogFragment(this, task)
        dialog.show(supportFragmentManager, "Task")
    }
}
