package com.majdus.organisateur

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

class AlarmAdapter(private val activity: Alarms, private val list: List<String>) :
    RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.label)
        val deleteButton: ImageButton = view.findViewById(R.id.delete)
        val editButton: ImageButton = view.findViewById(R.id.edit)
        val alarmSwitch: MaterialSwitch = view.findViewById(R.id.alarmSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(activity).inflate(R.layout.layout_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val text = list[position]
        holder.textView.text = text

        holder.alarmSwitch.setOnCheckedChangeListener(null)
        holder.alarmSwitch.isChecked = AlarmScheduler.isEnabled(activity, text)
        holder.alarmSwitch.setOnCheckedChangeListener { _, isChecked -> activity.alarmStatusChanged(text, isChecked) }

        holder.deleteButton.setOnClickListener { activity.removeItem(text) }
        holder.editButton.setOnClickListener { activity.edit(text) }
    }

    override fun getItemCount() = list.size
}
