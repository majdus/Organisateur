package com.majdus.organisateur

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.majdus.organisateur.data.Task

/**
 * Liste des tâches, réorganisable au doigt.
 *
 * Volontairement un adaptateur ordinaire et non un `ListAdapter`: ce dernier calcule ses
 * différences en tâche de fond, ce qui convient à un rafraîchissement mais pas à un glisser —
 * chaque franchissement de carte arriverait avec une image ou deux de retard. [moveItem] agit
 * donc directement sur la liste, sur le fil principal, et [submit] réserve le calcul de
 * différences aux rechargements — c'est lui qui anime une tâche cochée jusqu'à sa nouvelle place
 * au lieu de reconstruire la liste entière.
 */
class TaskAdapter(
    private val onToggle: (Task, Boolean) -> Unit,
    private val onClick: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private val tasks = mutableListOf<Task>()

    override fun getItemCount(): Int = tasks.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position], onToggle, onClick)
    }

    /** La tâche affichée à cette place, ou `null` si la liste a bougé entre-temps. */
    fun itemAt(position: Int): Task? = tasks.getOrNull(position)

    /** Remplacement du contenu après lecture en base. */
    fun submit(newTasks: List<Task>) {
        val diff = DiffUtil.calculateDiff(Difference(tasks.toList(), newTasks), true)
        tasks.clear()
        tasks.addAll(newTasks)
        diff.dispatchUpdatesTo(this)
    }

    /**
     * Déplacement d'une carte pendant le glisser. `notifyItemMoved` est ce qui fait glisser les
     * voisines vers leur nouvelle place au lieu de les faire clignoter.
     */
    fun moveItem(from: Int, to: Int) {
        if (from !in tasks.indices || to !in tasks.indices) return
        tasks.add(to, tasks.removeAt(from))
        notifyItemMoved(from, to)
    }

    /** L'ordre affiché à l'instant, à enregistrer une fois la carte relâchée. */
    fun currentOrderIds(): List<String> = tasks.map { it.id }

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
            card.contentDescription =
                context.getString(R.string.task_item_description, task.title) +
                        ", " + context.getString(R.string.task_reorder_hint)
        }
    }

    private class Difference(
        private val oldTasks: List<Task>,
        private val newTasks: List<Task>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldTasks.size

        override fun getNewListSize(): Int = newTasks.size

        override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean =
            oldTasks[oldPosition].id == newTasks[newPosition].id

        override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean {
            val old = oldTasks[oldPosition]
            val new = newTasks[newPosition]
            return old.title == new.title &&
                    old.isCompleted == new.isCompleted &&
                    old.timestamp == new.timestamp
        }
    }
}
