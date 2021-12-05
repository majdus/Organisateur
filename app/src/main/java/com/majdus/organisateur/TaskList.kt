package com.majdus.organisateur

import android.os.Bundle
import android.widget.ListView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class TaskList : ListActivity() {
    private lateinit var tasks: ListView
    private lateinit var addTask: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_list)
        tag = "tasks"
        addItemMessage = "Nouvelle tâche"
        removeItemMessage = "Supprimer cette tâche?"
        removedToast = "Tâche supprimée!"
        successToast = "Nouvelle tâche ajoutée!"
        errorToast = "La tâche ne peux pas être vide. Saisissez un text avant de valider!"

        tasks = findViewById(R.id.tasks)

        addTask = findViewById(R.id.addTask)
        addTask.setOnClickListener { addItem() }

        initList(this)
        loadItems()
        tasks.adapter = adapter
    }
}
