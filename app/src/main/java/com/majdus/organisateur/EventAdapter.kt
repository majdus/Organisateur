package com.majdus.organisateur

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.majdus.organisateur.data.Event

class EventAdapter(private val activity: Calendrier, private var list: List<Event>) :
    RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    fun updateEvents(newEvents: List<Event>) {
        list = newEvents
        notifyDataSetChanged()
    }

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.label)
        val timeContainer: View = view.findViewById(R.id.timeContainer)
        val bellIcon: View = view.findViewById(R.id.bellIcon)
        val timeLabel: TextView = view.findViewById(R.id.timeLabel)
        val deleteButton: ImageButton = view.findViewById(R.id.delete)
        val editButton: ImageButton = view.findViewById(R.id.edit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(activity).inflate(R.layout.layout_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = list[position]
        holder.textView.text = event.title
        
        holder.timeContainer.visibility = View.VISIBLE
        holder.timeLabel.text = String.format("%02d:%02d", event.hour, event.minute)
        holder.bellIcon.visibility = if (event.hasNotification) View.VISIBLE else View.GONE

        holder.deleteButton.setOnClickListener { activity.removeEvent(event) }
        holder.editButton.setOnClickListener { activity.editEvent(event) }
    }

    override fun getItemCount() = list.size
}
