package com.majdus.organisateur

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import androidx.appcompat.widget.SwitchCompat

class InteractiveArrayAdapter(private val activity: ListActivity, list: List<String>, private val isAlarm: Boolean = false) :
    ArrayAdapter<String>(activity, 0, list) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val text = getItem(position)
        if (view == null) {
            if (isAlarm) {
                view = LayoutInflater.from(activity).inflate(R.layout.layout_alarm, parent, false)
            } else {
                view = LayoutInflater.from(activity).inflate(R.layout.layout_task, parent, false)
            }
        }
        val textView: TextView = view!!.findViewById(R.id.label)
        val deleteButton: ImageButton = view.findViewById(R.id.delete)
        val editButton: ImageButton = view.findViewById(R.id.edit)
        if (isAlarm) {
            val alarmSwitch = view.findViewById<SwitchCompat>(R.id.alarmSwitch)
            alarmSwitch.setOnCheckedChangeListener { _, isChecked -> activity.alarmStatusChanged(isChecked)  }
        }
        textView.text = text
        deleteButton.setOnClickListener { activity.removeItem(text!!) }
        editButton.setOnClickListener { activity.edit(text!!) }
        return view
    }
}
