package com.majdus.organisateur

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.ListView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class Notifications : ListActivity() {

    private lateinit var alarms: ListView
    private lateinit var addAlarm: FloatingActionButton

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

        addAlarm = findViewById(R.id.addAlarm)
        addAlarm.setOnClickListener { addAlarmItem() }

        initList(this, true)
        loadItems()
        alarms.adapter = adapter

        sharedPreferences = getSharedPreferences("organisateur", Context.MODE_PRIVATE)
    }

    override fun edit(text: String) {
        val last = text.split("\n").last()
        val ts = last.split(":")
        val hour = ts.first()
        val minute = ts.last()
        editAlarmItem(text.removeSuffix(last).trim(), text, hour, minute)
    }

    override fun alarmStatusChanged(checked: Boolean) {
        Log.d("Alarm", "Alarm $checked")
    }
}