package com.majdus.organisateur

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class Alarms : ListActivity() {

    private lateinit var alarms: RecyclerView
    private lateinit var addAlarm: android.widget.ImageButton
    private lateinit var alarmAdapter: AlarmAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        tag = "alarms"
        addItemMessage = "Nouvelle Alarm"
        removeItemMessage = "Supprimer cette alarm?"
        removedToast = "Alarm supprimée!"
        successToast = "Nouvelle alarm ajoutée!"
        updateToast = "Alarm mise à jour!"
        errorToast = "Le text de l'alarm ne peux pas être vide. Saisissez un text avant de valider!"

        alarms = findViewById(R.id.alarms)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        addAlarm = findViewById(R.id.addAlarm)
        addAlarm.setOnClickListener { addAlarmItem() }

        sharedPreferences = getSharedPreferences("organisateur", Context.MODE_PRIVATE)
        loadItems()
        
        alarmAdapter = AlarmAdapter(this, list)
        alarms.adapter = alarmAdapter
    }

    override fun notifyDataChanged() {
        alarmAdapter.notifyDataSetChanged()
    }

    override fun edit(text: String) {
        val last = text.split("\n").last()
        val ts = last.split(":")
        val hour = ts.first()
        val minute = ts.last()
        editAlarmItem(text.removeSuffix(last).trim(), text, hour, minute)
    }

    override fun alarmStatusChanged(text: String, checked: Boolean) {
        Log.d("Alarm", "Alarm \"$text\" enabled=$checked")
        AlarmScheduler.setEnabled(this, text, checked)
    }

    override fun onItemRemoved(text: String) {
        AlarmScheduler.forget(this, text)
    }
}