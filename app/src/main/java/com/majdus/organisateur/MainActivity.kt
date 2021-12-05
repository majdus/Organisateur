package com.majdus.organisateur

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton

class MainActivity : AppCompatActivity() {
    private lateinit var buttonTasks: ImageButton
    private lateinit var buttonNotes: ImageButton
    private lateinit var buttonNotifications: ImageButton
    private lateinit var buttonCalendrier: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buttonTasks = findViewById(R.id.buttonTasks)
        buttonTasks.setOnClickListener { startTasksActivity() }

        buttonNotes = findViewById(R.id.buttonNotes)
        buttonNotes.setOnClickListener { startNotesActivity() }

        buttonNotifications = findViewById(R.id.buttonNotifications)
        buttonNotifications.setOnClickListener { startNotificationsActivity() }

        buttonCalendrier = findViewById(R.id.buttonCalendrier)
        buttonCalendrier.setOnClickListener { startCalendrierActivity() }
    }

    private fun startTasksActivity() {
        val intent = Intent(this, TaskList::class.java)
        startActivity(intent)
    }

    private fun startNotesActivity() {
        val intent = Intent(this, Notes::class.java)
        startActivity(intent)
    }

    private fun startNotificationsActivity() {
        val intent = Intent(this, Notifications::class.java)
        startActivity(intent)
    }

    private fun startCalendrierActivity() {
        val intent = Intent(this, Calendrier::class.java)
        startActivity(intent)
    }
}