package com.majdus.organisateur

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Locale

class AlarmDialogFragment(private val listActivity: ListActivity, private val hour: Int, private val minutes: Int,
                          private val text: String?, private val oldText: String?) : DialogFragment() {
    
    private var selectedHour: Int = 0
    private var selectedMinute: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = MaterialAlertDialogBuilder(it)
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_alarm, null)

            val alarmTextEdit = view.findViewById<TextInputEditText>(R.id.alarmDescription)
            val timeButton = view.findViewById<MaterialButton>(R.id.timeButton)
            val btnSave = view.findViewById<android.widget.Button>(R.id.btnSave)
            val btnCancel = view.findViewById<android.widget.Button>(R.id.btnCancel)
            val dialogTitle = view.findViewById<android.widget.TextView>(R.id.dialogTitle)

            var currentHour = hour
            var currentMinute = minutes

            if (text != null) {
                alarmTextEdit.setText(text)
                currentHour = hour
                currentMinute = minutes
                timeButton.text = String.format("%02d:%02d", currentHour, currentMinute)
                dialogTitle.setText(R.string.set)
            } else {
                dialogTitle.setText("Nouveau Rappel")
                timeButton.text = String.format("%02d:%02d", currentHour, currentMinute)
            }

            timeButton.setOnClickListener {
                val picker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(currentHour)
                    .setMinute(currentMinute)
                    .setTitleText("Sélectionnez l'heure de l'alarme")
                    .build()
                picker.addOnPositiveButtonClickListener {
                    currentHour = picker.hour
                    currentMinute = picker.minute
                    timeButton.text = String.format("%02d:%02d", currentHour, currentMinute)
                }
                picker.show(parentFragmentManager, "time_picker")
            }

            builder.setView(view)
            builder.setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            
            val dialog = builder.create()

            btnCancel.setOnClickListener {
                dialog.cancel()
            }

            btnSave.setOnClickListener {
                val newText = alarmTextEdit.text.toString()
                if (newText.trim().isEmpty()) {
                    alarmTextEdit.error = "L'alarme ne peut pas être vide"
                } else {
                    if (text == null) {
                        addAlarm(newText, currentHour, currentMinute)
                    } else {
                        updateAlarm(oldText!!, newText, currentHour, currentMinute)
                    }
                    dialog.dismiss()
                }
            }
            dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }
    private fun addAlarm(alarmDescription: String, hour: Int, minute: Int) {
        val alarmText = "$alarmDescription\n$hour:$minute"
        listActivity.addNewItem(alarmText)
        AlarmScheduler.schedule(listActivity, alarmText)
        Toast.makeText(context, "Alarme \"$alarmDescription\" programmée à ${format(hour, minute)}", Toast.LENGTH_LONG).show()
    }

    private fun updateAlarm(oldAlarmText: String, alarmDescription: String, hour: Int, minute: Int) {
        val alarmText = "$alarmDescription\n$hour:$minute"
        AlarmScheduler.forget(listActivity, oldAlarmText)
        listActivity.updateItem(oldAlarmText, alarmText)
        AlarmScheduler.setEnabled(listActivity, alarmText, true)
        Toast.makeText(context, "Alarme \"$alarmDescription\" reprogrammée à ${format(hour, minute)}", Toast.LENGTH_LONG).show()
    }

    private fun format(hour: Int, minute: Int) = String.format("%02d:%02d", hour, minute)
}
