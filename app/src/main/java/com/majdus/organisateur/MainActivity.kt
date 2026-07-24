package com.majdus.organisateur

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageButton

class MainActivity : AppCompatActivity() {
    private lateinit var cardTasks: android.widget.LinearLayout
    private lateinit var cardNotes: android.widget.LinearLayout
    private lateinit var cardNotifications: android.widget.LinearLayout
    private lateinit var cardCalendrier: android.widget.LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        cardTasks = findViewById(R.id.cardTasks)
        cardTasks.setOnClickListener { startTasksActivity() }

        cardNotes = findViewById(R.id.cardNotes)
        cardNotes.setOnClickListener { startNotesActivity() }

        cardNotifications = findViewById(R.id.cardNotifications)
        cardNotifications.setOnClickListener { startNotificationsActivity() }

        cardCalendrier = findViewById(R.id.cardCalendrier)
        cardCalendrier.setOnClickListener { startCalendrierActivity() }
    }

    private fun checkPermissions() {
        // Request POST_NOTIFICATIONS for Android 13+ (API 33)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        // Check for EXACT_ALARM for Android 14+ (API 34)
        if (android.os.Build.VERSION.SDK_INT >= 34) { // Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
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
        val intent = Intent(this, Alarms::class.java)
        startActivity(intent)
    }

    private fun startCalendrierActivity() {
        val intent = Intent(this, Calendrier::class.java)
        startActivity(intent)
    }
}