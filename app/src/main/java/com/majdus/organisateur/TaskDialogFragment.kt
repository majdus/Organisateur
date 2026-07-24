package com.majdus.organisateur

import android.app.Dialog
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.majdus.organisateur.data.Task

class TaskDialogFragment(private val taskList: TaskList, private val task: Task?) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = MaterialAlertDialogBuilder(it)
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_task, null)

            val taskTextEdit = view.findViewById<TextInputEditText>(R.id.taskDescription)
            val btnSave = view.findViewById<android.widget.Button>(R.id.btnSave)
            val btnCancel = view.findViewById<android.widget.Button>(R.id.btnCancel)
            val dialogTitle = view.findViewById<android.widget.TextView>(R.id.dialogTitle)

            if (task != null) {
                taskTextEdit.setText(task.title)
                dialogTitle.setText(R.string.set)
            } else {
                dialogTitle.setText(R.string.new_task)
            }

            builder.setView(view)
            
            // Set the background to transparent so our custom rounded white card background shows
            builder.setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

            val dialog = builder.create()
            
            btnCancel.setOnClickListener {
                dialog.cancel()
            }

            btnSave.setOnClickListener {
                val newText = taskTextEdit.text.toString()
                if (newText.trim().isEmpty()) {
                    taskTextEdit.error = "La tâche ne peut pas être vide"
                } else {
                    if (task == null) {
                        taskList.addNewItem(Task(title = newText))
                    } else {
                        task.title = newText
                        taskList.updateItem(task)
                    }
                    dialog.dismiss()
                }
            }
            dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
