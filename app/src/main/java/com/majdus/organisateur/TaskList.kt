package com.majdus.organisateur

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class TaskList : AppCompatActivity() {

    private lateinit var tasks: ListView
    private lateinit var addTask: FloatingActionButton
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var adapter: ArrayAdapter<String>

    private var list = ArrayList<String>()
    private var set = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)

        tasks = findViewById(R.id.tasks)
        adapter = InteractiveArrayAdapter(
            this,
            list
        )

        tasks.adapter = adapter

        addTask = findViewById(R.id.addTask)
        addTask.setOnClickListener { addNewTask() }

        sharedPreferences = getPreferences(Context.MODE_PRIVATE)
        loadTasks()
    }

    private fun loadTasks() {
        set = sharedPreferences.getStringSet("tasks", set) as HashSet<String>

        for (s: String in set) {
            list.add(s)
        }
    }

    private fun addNewTask() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Nouvelle tache")

        val input = EditText(this)
        builder.setView(input)
        builder.setPositiveButton(
            "Créer"
        ) { _, _ ->  addNewTask(input.text.toString()) }
        builder.setNegativeButton(
            "Annuler"
        ) { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun addNewTask(text: String) {
        list.add(text)
        set.add(text)
        with (sharedPreferences.edit()) {
            putStringSet("tasks", set)
            apply()
        }
    }

    fun removeTask(text: String) {
        list.remove(text)
        set.remove(text)
    }
}
