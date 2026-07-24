package com.majdus.organisateur

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.majdus.organisateur.data.Task

class TaskAdapter(private val activity: TaskList, private var list: List<Task>) :
    RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    fun updateTasks(newTasks: List<Task>) {
        list = newTasks
        notifyDataSetChanged()
    }

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.label)
        val deleteButton: ImageButton = view.findViewById(R.id.delete)
        val editButton: ImageButton = view.findViewById(R.id.edit)
        val checkBox: android.widget.CheckBox = view.findViewById(R.id.checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(activity).inflate(R.layout.layout_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = list[position]
        holder.textView.text = task.title

        holder.checkBox.setOnCheckedChangeListener(null)
        val isCompleted = task.isCompleted
        holder.checkBox.isChecked = isCompleted
        applyStrikethrough(holder.textView, isCompleted)

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            task.isCompleted = isChecked
            activity.updateItem(task)
        }

        holder.deleteButton.setOnClickListener { activity.removeItem(task) }
        holder.editButton.setOnClickListener { activity.editItem(task) }
    }

    override fun getItemCount() = list.size

    private fun applyStrikethrough(textView: TextView, isCompleted: Boolean) {
        if (isCompleted) {
            textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            textView.alpha = 0.5f
        } else {
            textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            textView.alpha = 1.0f
        }
    }
}
