package com.majdus.organisateur

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

/**
 * Feuille de création / modification d'un rappel.
 *
 * Aucun état n'est passé par le constructeur: le fragment est recréé par le système lors
 * d'une rotation, et le résultat remonte par `FragmentResult` plutôt que par une référence
 * à l'activité — la feuille survit donc aux changements de configuration.
 */
class AlarmEditSheet : BottomSheetDialogFragment() {

    private var hour = 0
    private var minute = 0
    private var original: Alarm? = null

    private lateinit var timeView: TextView
    private lateinit var previewView: TextView
    private lateinit var labelInput: TextInputEditText
    private lateinit var labelLayout: TextInputLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_alarm_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        original = readAlarm(requireArguments(), PREFIX_ORIGINAL)
        val isEdit = original != null

        timeView = view.findViewById(R.id.textTime)
        previewView = view.findViewById(R.id.textPreview)
        labelInput = view.findViewById(R.id.alarmLabel)
        labelLayout = view.findViewById(R.id.labelInputLayout)
        val title: TextView = view.findViewById(R.id.sheetTitle)
        val timeField: View = view.findViewById(R.id.timeField)
        val saveButton: MaterialButton = view.findViewById(R.id.btnSave)
        val cancelButton: MaterialButton = view.findViewById(R.id.btnCancel)
        val deleteButton: MaterialButton = view.findViewById(R.id.btnDelete)

        if (savedInstanceState != null) {
            hour = savedInstanceState.getInt(STATE_HOUR)
            minute = savedInstanceState.getInt(STATE_MINUTE)
        } else {
            // Par défaut la prochaine heure ronde: proposer l'heure courante ferait sonner
            // le rappel dans 24 h, ce qui n'est jamais l'intention.
            val suggested = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
            hour = original?.hour ?: suggested.get(Calendar.HOUR_OF_DAY)
            minute = original?.minute ?: 0
            labelInput.setText(original?.label.orEmpty())
        }

        title.setText(if (isEdit) R.string.alarm_edit_title else R.string.alarm_new_title)
        deleteButton.visibility = if (isEdit) View.VISIBLE else View.GONE
        renderTime()

        timeField.setOnClickListener { showTimePicker() }
        labelInput.addTextChangedListener { labelLayout.error = null }
        // Valider au clavier: le bouton Enregistrer passe sous le clavier sur les petits écrans.
        labelInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save()
                true
            } else {
                false
            }
        }
        cancelButton.setOnClickListener { dismiss() }
        saveButton.setOnClickListener { save() }
        deleteButton.setOnClickListener {
            val alarm = original ?: return@setOnClickListener
            sendResult(ACTION_DELETE, alarm)
        }

        // La feuille survit à une rotation: le sélecteur d'heure déjà affiché a perdu son
        // écouteur, il faut le rebrancher sur l'instance restaurée.
        (childFragmentManager.findFragmentByTag(TAG_TIME_PICKER) as? MaterialTimePicker)
            ?.let { attachTimePickerListener(it) }

        applySoftInputMode(showKeyboard = !isEdit && savedInstanceState == null)
    }

    /** La feuille doit se redimensionner au-dessus du clavier, pas être masquée par lui. */
    @Suppress("DEPRECATION") // SOFT_INPUT_ADJUST_RESIZE reste la seule option pour une Dialog.
    private fun applySoftInputMode(showKeyboard: Boolean) {
        if (showKeyboard) {
            labelInput.requestFocus()
            dialog?.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
        } else {
            dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_HOUR, hour)
        outState.putInt(STATE_MINUTE, minute)
    }

    private fun showTimePicker() {
        // Sans cela le sélecteur s'ouvre en saisie clavier quand le champ intitulé a le focus.
        labelInput.clearFocus()
        ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(labelInput.windowToken, 0)

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .setTheme(R.style.ThemeOverlay_Organisateur_TimePicker)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(R.string.alarm_time_picker_title)
            .build()
        attachTimePickerListener(picker)
        picker.show(childFragmentManager, TAG_TIME_PICKER)
    }

    private fun attachTimePickerListener(picker: MaterialTimePicker) {
        picker.addOnPositiveButtonClickListener {
            hour = picker.hour
            minute = picker.minute
            renderTime()
        }
    }

    /** Met à jour l'heure affichée et l'aperçu "Sonnera demain à 07:05 · dans 8 h 20". */
    private fun renderTime() {
        val preview = Alarm(PREVIEW_LABEL, hour, minute, enabled = true)
        timeView.text = preview.timeText
        previewView.text = AlarmFormatter.preview(requireContext(), preview)
    }

    private fun save() {
        val label = Alarm.sanitizeLabel(labelInput.text?.toString().orEmpty())
        if (label.isEmpty()) {
            labelLayout.error = getString(R.string.alarm_label_error)
            labelInput.requestFocus()
            return
        }
        // Un rappel réactivé à l'enregistrement: modifier une alarme éteinte la rallume,
        // ce qui correspond à l'intention de "je viens de la régler".
        sendResult(ACTION_SAVE, Alarm(label, hour, minute, enabled = true))
    }

    private fun sendResult(action: String, alarm: Alarm) {
        val result = bundleOf(KEY_ACTION to action)
        writeAlarm(result, PREFIX_ALARM, alarm)
        original?.let { writeAlarm(result, PREFIX_ORIGINAL, it) }
        parentFragmentManager.setFragmentResult(REQUEST_KEY, result)
        dismiss()
    }

    companion object {
        const val REQUEST_KEY = "alarm_edit_result"
        const val ACTION_SAVE = "save"
        const val ACTION_DELETE = "delete"

        private const val KEY_ACTION = "action"
        private const val PREFIX_ALARM = "alarm_"
        private const val PREFIX_ORIGINAL = "original_"
        private const val STATE_HOUR = "state_hour"
        private const val STATE_MINUTE = "state_minute"
        private const val TAG_TIME_PICKER = "alarm_time_picker"
        private const val PREVIEW_LABEL = "preview"

        fun newInstance(alarm: Alarm?): AlarmEditSheet = AlarmEditSheet().apply {
            arguments = Bundle().also { args -> alarm?.let { writeAlarm(args, PREFIX_ORIGINAL, it) } }
        }

        fun action(result: Bundle): String = result.getString(KEY_ACTION).orEmpty()

        fun editedAlarm(result: Bundle): Alarm? = readAlarm(result, PREFIX_ALARM)

        fun originalAlarm(result: Bundle): Alarm? = readAlarm(result, PREFIX_ORIGINAL)

        private fun writeAlarm(bundle: Bundle, prefix: String, alarm: Alarm) {
            bundle.putString(prefix + "label", alarm.label)
            bundle.putInt(prefix + "hour", alarm.hour)
            bundle.putInt(prefix + "minute", alarm.minute)
            bundle.putBoolean(prefix + "enabled", alarm.enabled)
        }

        private fun readAlarm(bundle: Bundle, prefix: String): Alarm? {
            val label = bundle.getString(prefix + "label") ?: return null
            return Alarm(
                label,
                bundle.getInt(prefix + "hour"),
                bundle.getInt(prefix + "minute"),
                bundle.getBoolean(prefix + "enabled", true)
            )
        }
    }
}
