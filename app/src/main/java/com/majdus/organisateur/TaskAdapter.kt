package com.majdus.organisateur

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.majdus.organisateur.data.Task

/**
 * Liste des tâches. Comme pour les rappels, [ListAdapter] + [DiffUtil] pour que cocher une
 * tâche l'anime jusqu'à sa nouvelle place au lieu de reconstruire la liste entière.
 */
class TaskAdapter(
    private val onToggle: (Task, Boolean) -> Unit,
    private val onClick: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position), onToggle, onClick)
    }

    class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.card)
        private val label: TextView = view.findViewById(R.id.label)
        private val meta: TextView = view.findViewById(R.id.textMeta)
        private val metaChip: View = view.findViewById(R.id.metaChip)
        private val checkbox: CheckBox = view.findViewById(R.id.checkbox)

        fun bind(task: Task, onToggle: (Task, Boolean) -> Unit, onClick: (Task) -> Unit) {
            val context = itemView.context
            label.text = task.title
            meta.text = context.getString(
                R.string.task_added,
                DateLabels.relativeDay(context, task.timestamp)
            )

            // Une tâche faite reste lisible mais visiblement en retrait, comme un rappel éteint.
            val completed = task.isCompleted
            label.paintFlags = if (completed) {
                label.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                label.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            val contentAlpha = if (completed) 0.45f else 1f
            label.alpha = contentAlpha
            metaChip.alpha = contentAlpha

            // Détaché avant setChecked: sinon la réutilisation d'une vue recyclée cocherait
            // fantomatiquement la tâche précédente.
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = completed
            checkbox.setOnCheckedChangeListener { _, isChecked -> onToggle(task, isChecked) }

            card.setOnClickListener { onClick(task) }
            card.contentDescription = context.getString(R.string.task_item_description, task.title)
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean =
                oldItem.title == newItem.title &&
                        oldItem.isCompleted == newItem.isCompleted &&
                        oldItem.timestamp == newItem.timestamp
        }
    }
}
