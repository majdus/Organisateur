package com.majdus.organisateur

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.majdus.organisateur.data.Event
import java.util.Calendar
import java.util.Locale

/**
 * Feuille de création / modification d'un événement.
 *
 * Même contrat que [AlarmEditSheet]: état passé par arguments, résultat remonté par
 * `FragmentResult`, donc la feuille survit à une rotation.
 */
class EventEditSheet : BottomSheetDialogFragment() {

    private var hour = 0
    private var minute = 0
    private var original: Event? = null
    private lateinit var date: String

    private lateinit var timeView: TextView
    private lateinit var labelInput: TextInputEditText
    private lateinit var labelLayout: TextInputLayout
    private lateinit var notificationSwitch: MaterialSwitch
    private lateinit var pastWarning: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_event_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val arguments = requireArguments()
        original = readEvent(arguments, PREFIX_ORIGINAL)
        date = original?.date ?: arguments.getString(KEY_DATE).orEmpty()
        val isEdit = original != null

        timeView = view.findViewById(R.id.textTime)
        labelInput = view.findViewById(R.id.eventLabel)
        labelLayout = view.findViewById(R.id.labelInputLayout)
        notificationSwitch = view.findViewById(R.id.notificationSwitch)
        pastWarning = view.findViewById(R.id.textPastWarning)
        val title: TextView = view.findViewById(R.id.sheetTitle)
        val dateView: TextView = view.findViewById(R.id.sheetDate)
        val timeField: View = view.findViewById(R.id.timeField)
        val saveButton: MaterialButton = view.findViewById(R.id.btnSave)
        val cancelButton: MaterialButton = view.findViewById(R.id.btnCancel)
        val deleteButton: MaterialButton = view.findViewById(R.id.btnDelete)

        if (savedInstanceState != null) {
            hour = savedInstanceState.getInt(STATE_HOUR)
            minute = savedInstanceState.getInt(STATE_MINUTE)
        } else {
            // Par défaut la prochaine heure ronde, comme pour un rappel.
            val suggested = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
            hour = original?.hour ?: suggested.get(Calendar.HOUR_OF_DAY)
            minute = original?.minute ?: 0
            labelInput.setText(original?.title.orEmpty())
            notificationSwitch.isChecked = original?.hasNotification ?: true
        }

        title.setText(if (isEdit) R.string.event_edit_title else R.string.event_new_title)
        dateView.text = DateLabels.weekday(dayStart())
            .replaceFirstChar { it.titlecase(Locale.FRANCE) }
        deleteButton.visibility = if (isEdit) View.VISIBLE else View.GONE
        renderTime()

        timeField.setOnClickListener { showTimePicker() }
        notificationSwitch.setOnCheckedChangeListener { _, _ -> renderPastWarning() }
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
            val event = original ?: return@setOnClickListener
            sendResult(ACTION_DELETE, event)
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
            .setTheme(R.style.ThemeOverlay_Organisateur_TimePicker_Calendar)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(R.string.event_time_picker_title)
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

    private fun renderTime() {
        timeView.text = String.format(Locale.FRANCE, "%02d:%02d", hour, minute)
        renderPastWarning()
    }

    /**
     * Une notification demandée pour un instant déjà passé ne partira jamais: le dire
     * pendant la saisie évite de le découvrir en n'étant pas averti.
     */
    private fun renderPastWarning() {
        val isPast = eventTimeInMillis() <= System.currentTimeMillis()
        pastWarning.visibility =
            if (notificationSwitch.isChecked && isPast) View.VISIBLE else View.GONE
    }

    private fun dayStart(): Long = EventTimes.dayStart(date)

    private fun eventTimeInMillis(): Long = EventTimes.at(date, hour, minute)

    private fun save() {
        val title = labelInput.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            labelLayout.error = getString(R.string.event_label_error)
            labelInput.requestFocus()
            return
        }
        val edited = original?.copy(
            title = title,
            hour = hour,
            minute = minute,
            hasNotification = notificationSwitch.isChecked
        ) ?: Event(
            date = date,
            title = title,
            hour = hour,
            minute = minute,
            hasNotification = notificationSwitch.isChecked
        )
        sendResult(ACTION_SAVE, edited)
    }

    private fun sendResult(action: String, event: Event) {
        val result = bundleOf(KEY_ACTION to action)
        writeEvent(result, PREFIX_EVENT, event)
        original?.let { writeEvent(result, PREFIX_ORIGINAL, it) }
        parentFragmentManager.setFragmentResult(REQUEST_KEY, result)
        dismiss()
    }

    companion object {
        const val REQUEST_KEY = "event_edit_result"
        const val ACTION_SAVE = "save"
        const val ACTION_DELETE = "delete"

        private const val KEY_ACTION = "action"
        private const val KEY_DATE = "date"
        private const val PREFIX_EVENT = "event_"
        private const val PREFIX_ORIGINAL = "original_"
        private const val STATE_HOUR = "state_hour"
        private const val STATE_MINUTE = "state_minute"
        private const val TAG_TIME_PICKER = "event_time_picker"

        /** [date] au format "yyyy-MM-dd"; ignoré quand [event] est fourni. */
        fun newInstance(event: Event?, date: String): EventEditSheet = EventEditSheet().apply {
            arguments = bundleOf(KEY_DATE to date).also { args ->
                event?.let { writeEvent(args, PREFIX_ORIGINAL, it) }
            }
        }

        fun action(result: Bundle): String = result.getString(KEY_ACTION).orEmpty()

        fun editedEvent(result: Bundle): Event? = readEvent(result, PREFIX_EVENT)

        fun originalEvent(result: Bundle): Event? = readEvent(result, PREFIX_ORIGINAL)

        private fun writeEvent(bundle: Bundle, prefix: String, event: Event) {
            bundle.putString(prefix + "id", event.id)
            bundle.putString(prefix + "date", event.date)
            bundle.putString(prefix + "title", event.title)
            bundle.putInt(prefix + "hour", event.hour)
            bundle.putInt(prefix + "minute", event.minute)
            bundle.putBoolean(prefix + "notify", event.hasNotification)
            bundle.putLong(prefix + "timestamp", event.timestamp)
        }

        private fun readEvent(bundle: Bundle, prefix: String): Event? {
            val id = bundle.getString(prefix + "id") ?: return null
            return Event(
                id = id,
                date = bundle.getString(prefix + "date").orEmpty(),
                title = bundle.getString(prefix + "title").orEmpty(),
                hour = bundle.getInt(prefix + "hour"),
                minute = bundle.getInt(prefix + "minute"),
                hasNotification = bundle.getBoolean(prefix + "notify"),
                timestamp = bundle.getLong(prefix + "timestamp")
            )
        }
    }
}
