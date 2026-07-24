package com.majdus.organisateur

import android.app.Dialog
import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.DialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.majdus.organisateur.data.Event
import java.util.Calendar
import java.util.Locale

class EventDialogFragment(private val calendrier: Calendrier, private val event: Event?, private val selectedDate: String) : DialogFragment() {
    private var selectedHour: Int = 0
    private var selectedMinute: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = MaterialAlertDialogBuilder(it)
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.dialog_event, null)

            val eventTextEdit = view.findViewById<TextInputEditText>(R.id.eventDescription)
            val timeButton = view.findViewById<MaterialButton>(R.id.timeButton)
            val notificationSwitch = view.findViewById<MaterialSwitch>(R.id.notificationSwitch)
            val btnSave = view.findViewById<android.widget.Button>(R.id.btnSave)
            val btnCancel = view.findViewById<android.widget.Button>(R.id.btnCancel)
            val dialogTitle = view.findViewById<android.widget.TextView>(R.id.dialogTitle)

            if (event != null) {
                eventTextEdit.setText(event.title)
                selectedHour = event.hour
                selectedMinute = event.minute
                notificationSwitch.isChecked = event.hasNotification
                dialogTitle.setText(R.string.set)
            } else {
                val cal = Calendar.getInstance()
                selectedHour = cal.get(Calendar.HOUR_OF_DAY)
                selectedMinute = cal.get(Calendar.MINUTE)
                dialogTitle.setText("Nouvel Événement")
            }

            val updateTimeButtonText = {
                timeButton.text = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
            }
            updateTimeButtonText()

            timeButton.setOnClickListener {
                val timePicker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_24H)
                    .setHour(selectedHour)
                    .setMinute(selectedMinute)
                    .setTitleText("Sélectionner l'heure")
                    .build()

                timePicker.addOnPositiveButtonClickListener {
                    selectedHour = timePicker.hour
                    selectedMinute = timePicker.minute
                    updateTimeButtonText()
                }

                timePicker.show(parentFragmentManager, "timePicker")
            }

            builder.setView(view)
            builder.setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            val dialog = builder.create()
            
            btnCancel.setOnClickListener {
                dialog.cancel()
            }

            btnSave.setOnClickListener {
                val newText = eventTextEdit.text.toString()
                val hasNotif = notificationSwitch.isChecked

                if (newText.trim().isEmpty()) {
                    eventTextEdit.error = "L'événement ne peut pas être vide"
                } else {
                    if (event == null) {
                        calendrier.addNewEvent(Event(date = selectedDate, title = newText, hour = selectedHour, minute = selectedMinute, hasNotification = hasNotif))
                    } else {
                        event.title = newText
                        event.hour = selectedHour
                        event.minute = selectedMinute
                        event.hasNotification = hasNotif
                        calendrier.updateEvent(event)
                    }
                    dialog.dismiss()
                }
            }
            dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}
