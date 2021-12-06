package com.majdus.organisateur

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class TaskDialogFragment(private val listActivity: ListActivity, private val text: String?) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_task, null)

            val taskTextEdit = view.findViewById<EditText>(R.id.taskDescription)
            if (text != null) {
                taskTextEdit.setText(text)
            }

            builder.setView(view)
                // Add action buttons
                .setPositiveButton(R.string.set
                ) { _, _ ->
                    if (text == null) {
                        listActivity.addNewItem(taskTextEdit.text.toString())
                    } else {
                        listActivity.updateItem(text, taskTextEdit.text.toString())
                    }
                }
                .setNegativeButton(R.string.cancel
                ) { dialog, _ ->
                    dialog.cancel()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
