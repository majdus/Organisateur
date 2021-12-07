package com.majdus.organisateur

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class AlarmDialogFragment(private val listActivity: ListActivity, private val hour: Int, private val minutes: Int,
                          private val text: String?, private val oldText: String?) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_alarm, null)

            val alarmTextEdit = view.findViewById<EditText>(R.id.alarmDescription)
            if (text != null) {
                alarmTextEdit.setText(text)
            }
            val alarmTimePicker = view.findViewById<TimePicker>(R.id.timePicker)
            alarmTimePicker.setIs24HourView(true)
            alarmTimePicker.hour = hour
            alarmTimePicker.minute = minutes
            builder.setView(view)
                .setPositiveButton(R.string.set
                ) { _, _ ->
                    if (text == null) {
                        addAlarm(
                            alarmTextEdit.text.toString(),
                            alarmTimePicker.hour,
                            alarmTimePicker.minute
                        )
                    } else {
                        updateAlarm(
                            oldText!!,
                            alarmTextEdit.text.toString(),
                            alarmTimePicker.hour,
                            alarmTimePicker.minute
                        )
                    }
                }
                .setNegativeButton(R.string.cancel
                ) { dialog, _ ->
                    dialog.cancel()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun addAlarm(alarmDescription: String, hour: Int, minute: Int) {
        if (alarmDescription.isEmpty()) {
            Toast.makeText(context,"Ajoutez une description avant de valider.", Toast.LENGTH_LONG).show()
            return
        }

        listActivity.addNewItem("$alarmDescription\n$hour:$minute")
        createAlarm(alarmDescription, hour, minute)
    }

    private fun updateAlarm(oldDescription: String, alarmDescription: String, hour: Int, minute: Int) {
        listActivity.updateItem(oldDescription,"$alarmDescription\n$hour:$minute")
        updateAlarm(alarmDescription, hour, minute)
    }

    private fun createAlarm(alarmDescription: String, hour: Int, minute: Int) {
        Toast.makeText(context, "Alarm $alarmDescription created at $hour:$minute", Toast.LENGTH_LONG).show()
    }

    private fun updateAlarm(alarmDescription: String, hour: Int, minute: Int) {
        Toast.makeText(context, "Alarm $alarmDescription updated at $hour:$minute", Toast.LENGTH_LONG).show()
    }
}
