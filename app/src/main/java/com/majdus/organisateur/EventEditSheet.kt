package com.majdus.organisateur

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.majdus.organisateur.agenda.EventDraft
import com.majdus.organisateur.agenda.Zones
import com.majdus.organisateur.data.Event
import java.time.LocalDate
import java.util.Calendar

/**
 * Feuille de création / modification d'un événement.
 *
 * Même contrat que [AlarmEditSheet]: état passé par arguments, résultat remonté par
 * `FragmentResult`, donc la feuille survit à une rotation. Elle ne renvoie pas une entité mais un
 * [EventDraft] et l'occurrence visée: c'est au dépôt de décider ce que cela écrit, et à l'écran
 * de demander la portée quand il s'agit d'une série.
 */
class EventEditSheet : BottomSheetDialogFragment() {

    private var startUtc = 0L
    private var endUtc = 0L
    private var allDay = false
    private var colorKey = Event.DEFAULT_COLOR
    private var rrule = ""
    private val reminders = sortedSetOf<Int>()

    private var targetEventId: String? = null
    private var targetOccurrenceStart = 0L
    private var targetIsSeries = false

    private lateinit var labelInput: TextInputEditText
    private lateinit var labelLayout: TextInputLayout
    private lateinit var locationInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var allDaySwitch: MaterialSwitch
    private lateinit var startDateButton: MaterialButton
    private lateinit var startTimeButton: MaterialButton
    private lateinit var endDateButton: MaterialButton
    private lateinit var endTimeButton: MaterialButton
    private lateinit var endError: TextView
    private lateinit var colorRow: LinearLayout
    private lateinit var repeatSummary: TextView
    private lateinit var remindersRow: LinearLayout
    private lateinit var noReminder: TextView
    private lateinit var pastWarning: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_event_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val arguments = requireArguments()
        targetEventId = arguments.getString(KEY_EVENT_ID)
        targetOccurrenceStart = arguments.getLong(KEY_OCCURRENCE_START)
        targetIsSeries = arguments.getBoolean(KEY_IS_SERIES)
        val isEdit = targetEventId != null

        labelInput = view.findViewById(R.id.eventLabel)
        labelLayout = view.findViewById(R.id.labelInputLayout)
        locationInput = view.findViewById(R.id.eventLocation)
        descriptionInput = view.findViewById(R.id.eventDescription)
        allDaySwitch = view.findViewById(R.id.allDaySwitch)
        startDateButton = view.findViewById(R.id.startDateButton)
        startTimeButton = view.findViewById(R.id.startTimeButton)
        endDateButton = view.findViewById(R.id.endDateButton)
        endTimeButton = view.findViewById(R.id.endTimeButton)
        endError = view.findViewById(R.id.textEndError)
        colorRow = view.findViewById(R.id.colorRow)
        repeatSummary = view.findViewById(R.id.repeatSummary)
        remindersRow = view.findViewById(R.id.remindersRow)
        noReminder = view.findViewById(R.id.textNoReminder)
        pastWarning = view.findViewById(R.id.textPastWarning)

        restoreState(savedInstanceState, arguments, isEdit)
        buildColorRow()

        view.findViewById<TextView>(R.id.sheetTitle)
            .setText(if (isEdit) R.string.event_edit_title else R.string.event_new_title)
        val deleteButton: MaterialButton = view.findViewById(R.id.btnDelete)
        deleteButton.visibility = if (isEdit) View.VISIBLE else View.GONE

        allDaySwitch.setOnCheckedChangeListener { _, checked -> setAllDay(checked) }
        startDateButton.setOnClickListener { pickDate(isStart = true) }
        startTimeButton.setOnClickListener { pickTime(isStart = true) }
        endDateButton.setOnClickListener { pickDate(isStart = false) }
        endTimeButton.setOnClickListener { pickTime(isStart = false) }
        view.findViewById<View>(R.id.repeatRow).setOnClickListener { pickRecurrence() }
        view.findViewById<MaterialButton>(R.id.btnAddReminder).setOnClickListener { addReminder(it) }
        labelInput.addTextChangedListener { labelLayout.error = null }
        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { dismiss() }
        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        deleteButton.setOnClickListener { sendResult(ACTION_DELETE) }

        childFragmentManager.setFragmentResultListener(
            RecurrencePickerSheet.REQUEST_KEY, this
        ) { _, result ->
            rrule = RecurrencePickerSheet.rrule(result)
            render()
        }

        render()
        applySoftInputMode(showKeyboard = !isEdit && savedInstanceState == null)
    }

    private fun restoreState(savedInstanceState: Bundle?, arguments: Bundle, isEdit: Boolean) {
        val source = savedInstanceState ?: arguments
        allDay = source.getBoolean(KEY_ALL_DAY)
        colorKey = source.getString(KEY_COLOR) ?: Event.DEFAULT_COLOR
        rrule = source.getString(KEY_RRULE).orEmpty()
        reminders.addAll(source.getIntArray(KEY_REMINDERS)?.toList().orEmpty())

        if (savedInstanceState != null) {
            startUtc = savedInstanceState.getLong(KEY_START)
            endUtc = savedInstanceState.getLong(KEY_END)
            return
        }

        if (isEdit) {
            startUtc = arguments.getLong(KEY_START)
            endUtc = arguments.getLong(KEY_END)
            labelInput.setText(arguments.getString(KEY_TITLE).orEmpty())
            locationInput.setText(arguments.getString(KEY_LOCATION).orEmpty())
            descriptionInput.setText(arguments.getString(KEY_DESCRIPTION).orEmpty())
            return
        }

        // Création. Un créneau tapoté dans une grille dit déjà l'heure voulue; sinon on propose
        // la prochaine heure ronde du jour affiché, comme pour un rappel.
        val suggestedStart = arguments.getLong(KEY_SUGGESTED_START)
        startUtc = if (suggestedStart != 0L) {
            suggestedStart
        } else {
            val date = arguments.getString(KEY_DATE).orEmpty()
            val nextHour = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
            EventTimes.at(date, nextHour.get(Calendar.HOUR_OF_DAY), 0)
        }
        endUtc = EventTimes.defaultEnd(startUtc)
        reminders.add(0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_START, startUtc)
        outState.putLong(KEY_END, endUtc)
        outState.putBoolean(KEY_ALL_DAY, allDay)
        outState.putString(KEY_COLOR, colorKey)
        outState.putString(KEY_RRULE, rrule)
        outState.putIntArray(KEY_REMINDERS, reminders.toIntArray())
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

    private fun render() {
        allDaySwitch.isChecked = allDay
        startTimeButton.visibility = if (allDay) View.GONE else View.VISIBLE
        endTimeButton.visibility = if (allDay) View.GONE else View.VISIBLE

        startDateButton.text = dayLabel(startUtc)
        endDateButton.text = dayLabel(lastInstant())
        startTimeButton.text = DateLabels.time(startUtc)
        endTimeButton.text = DateLabels.time(endUtc)

        endError.visibility = if (endUtc > startUtc) View.GONE else View.VISIBLE
        repeatSummary.text = RecurrenceLabels.summary(requireContext(), rrule)

        renderColors()
        renderReminders()
    }

    /**
     * Libellé du jour d'un instant, selon l'ancrage courant.
     *
     * Le jour est résolu d'abord, puis reformaté depuis midi: une journée entière est ancrée à
     * minuit UTC, que le formateur local rendrait la veille sous certains fuseaux.
     */
    private fun dayLabel(instant: Long): String {
        val date = currentDate(instant)
        return DateLabels.absoluteDay(Zones.toUtc(date.atTime(NOON, 0), Zones.current()))
    }

    /**
     * Dernier instant compris dans l'événement.
     *
     * La fin est exclusive: le dernier jour se lit une milliseconde avant la borne, sans quoi un
     * événement d'une seule journée en annoncerait deux.
     */
    private fun lastInstant(): Long = (endUtc - 1).coerceAtLeast(startUtc)

    private fun currentDate(instant: Long): LocalDate =
        Zones.dateOf(instant, allDay, Zones.current())

    private fun buildColorRow() {
        val inflater = LayoutInflater.from(requireContext())
        for (swatch in EventColors.PALETTE) {
            val item = inflater.inflate(R.layout.item_color_swatch, colorRow, false)
            item.findViewById<View>(R.id.swatch).backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), swatch.colorRes)
            item.contentDescription = getString(swatch.labelRes)
            item.setOnClickListener {
                colorKey = swatch.key
                renderColors()
            }
            colorRow.addView(item)
        }
    }

    private fun renderColors() {
        for ((index, swatch) in EventColors.PALETTE.withIndex()) {
            val item = colorRow.getChildAt(index) ?: continue
            item.findViewById<ImageView>(R.id.check).visibility =
                if (swatch.key == colorKey) View.VISIBLE else View.GONE
        }
    }

    private fun renderReminders() {
        remindersRow.removeAllViews()
        noReminder.visibility = if (reminders.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(requireContext())
        for (minutes in reminders) {
            val item = inflater.inflate(R.layout.item_reminder, remindersRow, false)
            item.findViewById<TextView>(R.id.label).text =
                ReminderLabels.label(requireContext(), minutes)
            item.findViewById<View>(R.id.remove).setOnClickListener {
                reminders.remove(minutes)
                renderReminders()
                renderPastWarning()
            }
            remindersRow.addView(item)
        }
        renderPastWarning()
    }

    /**
     * Un rappel demandé pour un instant déjà passé ne partira jamais: le dire pendant la saisie
     * évite de le découvrir en n'étant pas averti.
     */
    private fun renderPastWarning() {
        val isPast = startUtc <= System.currentTimeMillis()
        pastWarning.visibility =
            if (reminders.isNotEmpty() && isPast) View.VISIBLE else View.GONE
    }

    private fun setAllDay(checked: Boolean) {
        if (checked == allDay) return
        val zone = Zones.current()
        if (checked) {
            val first = Zones.localDate(startUtc, zone)
            val last = Zones.localDate(lastInstant(), zone)
            startUtc = Zones.allDayStart(first)
            endUtc = Zones.allDayStart(last.plusDays(1))
        } else {
            val first = Zones.allDayDate(startUtc)
            val last = Zones.allDayDate(lastInstant())
            startUtc = Zones.toUtc(first.atTime(DEFAULT_HOUR, 0), zone)
            endUtc = Zones.toUtc(last.atTime(DEFAULT_HOUR + 1, 0), zone)
        }
        allDay = checked
        render()
    }

    private fun pickDate(isStart: Boolean) {
        val current = currentDate(if (isStart) startUtc else lastInstant())
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.event_date_picker_title)
            .setSelection(Zones.allDayStart(current))
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            // Le sélecteur raisonne en minuit UTC, comme l'ancrage des journées entières.
            applyDate(Zones.allDayDate(selection), isStart)
            render()
        }
        picker.show(childFragmentManager, TAG_DATE_PICKER)
    }

    private fun pickTime(isStart: Boolean) {
        val zone = Zones.current()
        val current = Zones.localDateTime(if (isStart) startUtc else endUtc, zone)
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
            .setTheme(R.style.ThemeOverlay_Organisateur_TimePicker_Calendar)
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(R.string.event_time_picker_title)
            .build()
        picker.addOnPositiveButtonClickListener {
            val date = Zones.localDate(if (isStart) startUtc else endUtc, zone)
            val moment = Zones.toUtc(date.atTime(picker.hour, picker.minute), zone)
            if (isStart) shiftStart(moment) else endUtc = moment
            render()
        }
        picker.show(childFragmentManager, TAG_TIME_PICKER)
    }

    private fun applyDate(date: LocalDate, isStart: Boolean) {
        val zone = Zones.current()
        if (isStart) {
            val moment = if (allDay) {
                Zones.allDayStart(date)
            } else {
                Zones.toUtc(date.atTime(Zones.localDateTime(startUtc, zone).toLocalTime()), zone)
            }
            shiftStart(moment)
        } else {
            endUtc = if (allDay) {
                // La date choisie est le dernier jour *inclus*; la borne stockée est le lendemain.
                Zones.allDayStart(date.plusDays(1))
            } else {
                Zones.toUtc(date.atTime(Zones.localDateTime(endUtc, zone).toLocalTime()), zone)
            }
        }
    }

    /** Déplacer le début emporte la fin: on garde la durée, comme partout ailleurs. */
    private fun shiftStart(moment: Long) {
        val duration = (endUtc - startUtc).coerceAtLeast(0)
        startUtc = moment
        endUtc = moment + duration
    }

    private fun pickRecurrence() {
        RecurrencePickerSheet.newInstance(rrule, startUtc)
            .show(childFragmentManager, TAG_RECURRENCE)
    }

    private fun addReminder(anchor: View) {
        val available = ReminderLabels.CHOICES.filterNot { it in reminders }
        if (available.isEmpty()) return

        val menu = PopupMenu(requireContext(), anchor)
        for ((index, minutes) in available.withIndex()) {
            menu.menu.add(0, index, index, ReminderLabels.label(requireContext(), minutes))
        }
        menu.setOnMenuItemClickListener { item ->
            reminders.add(available[item.itemId])
            renderReminders()
            true
        }
        menu.show()
    }

    private fun draft(): EventDraft = EventDraft(
        title = labelInput.text?.toString()?.trim().orEmpty(),
        startUtc = startUtc,
        endUtc = endUtc,
        allDay = allDay,
        description = descriptionInput.text?.toString()?.trim().orEmpty(),
        location = locationInput.text?.toString()?.trim().orEmpty(),
        colorKey = colorKey,
        rrule = rrule,
        reminders = reminders.toList()
    )

    private fun save() {
        if (labelInput.text?.toString()?.trim().isNullOrEmpty()) {
            labelLayout.error = getString(R.string.event_label_error)
            labelInput.requestFocus()
            return
        }
        if (endUtc <= startUtc) {
            endError.visibility = View.VISIBLE
            return
        }
        sendResult(ACTION_SAVE)
    }

    private fun sendResult(action: String) {
        val draft = draft()
        val result = bundleOf(
            KEY_ACTION to action,
            KEY_TITLE to draft.title,
            KEY_START to draft.startUtc,
            KEY_END to draft.endUtc,
            KEY_ALL_DAY to draft.allDay,
            KEY_DESCRIPTION to draft.description,
            KEY_LOCATION to draft.location,
            KEY_COLOR to draft.colorKey,
            KEY_RRULE to draft.rrule,
            KEY_REMINDERS to draft.reminders.toIntArray(),
            KEY_EVENT_ID to targetEventId,
            KEY_OCCURRENCE_START to targetOccurrenceStart,
            KEY_IS_SERIES to targetIsSeries
        )
        parentFragmentManager.setFragmentResult(REQUEST_KEY, result)
        dismiss()
    }

    companion object {
        const val REQUEST_KEY = "event_edit_result"
        const val ACTION_SAVE = "save"
        const val ACTION_DELETE = "delete"

        private const val KEY_ACTION = "action"
        private const val KEY_DATE = "date"
        private const val KEY_SUGGESTED_START = "suggested_start"
        private const val KEY_EVENT_ID = "event_id"
        private const val KEY_OCCURRENCE_START = "occurrence_start"
        private const val KEY_IS_SERIES = "is_series"
        private const val KEY_TITLE = "title"
        private const val KEY_START = "start"
        private const val KEY_END = "end"
        private const val KEY_ALL_DAY = "all_day"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_LOCATION = "location"
        private const val KEY_COLOR = "color"
        private const val KEY_RRULE = "rrule"
        private const val KEY_REMINDERS = "reminders"
        private const val TAG_TIME_PICKER = "event_time_picker"
        private const val TAG_DATE_PICKER = "event_date_picker"
        private const val TAG_RECURRENCE = "event_recurrence"
        private const val DEFAULT_HOUR = 9
        private const val NOON = 12

        /**
         * [date] au format "yyyy-MM-dd", pour une création; ignoré quand [event] est fourni.
         *
         * [occurrenceStartUtc] situe l'occurrence à modifier dans sa série — c'est elle que
         * l'éditeur montre, pas la première de la série. Elle est exprimée dans l'ancrage de
         * l'événement, donc en minuit UTC pour une journée entière.
         */
        fun newInstance(
            event: Event?,
            occurrenceStartUtc: Long,
            reminders: List<Int>,
            date: String,
            suggestedStartUtc: Long = 0L
        ): EventEditSheet = EventEditSheet().apply {
            val duration = event?.let { it.endUtc - it.startUtc } ?: 0L
            arguments = bundleOf(
                    KEY_DATE to date,
                    KEY_SUGGESTED_START to suggestedStartUtc,
                    KEY_EVENT_ID to event?.id,
                    KEY_OCCURRENCE_START to occurrenceStartUtc,
                    KEY_IS_SERIES to (event?.rrule?.isNotEmpty() ?: false),
                    KEY_TITLE to event?.title.orEmpty(),
                    KEY_START to occurrenceStartUtc,
                    KEY_END to (occurrenceStartUtc + duration),
                    KEY_ALL_DAY to (event?.allDay ?: false),
                    KEY_DESCRIPTION to event?.description.orEmpty(),
                    KEY_LOCATION to event?.location.orEmpty(),
                    KEY_COLOR to (event?.colorKey ?: Event.DEFAULT_COLOR),
                    KEY_RRULE to event?.rrule.orEmpty(),
                    KEY_REMINDERS to reminders.toIntArray()
                )
            }

        fun action(result: Bundle): String = result.getString(KEY_ACTION).orEmpty()

        fun draft(result: Bundle): EventDraft = EventDraft(
            title = result.getString(KEY_TITLE).orEmpty(),
            startUtc = result.getLong(KEY_START),
            endUtc = result.getLong(KEY_END),
            allDay = result.getBoolean(KEY_ALL_DAY),
            description = result.getString(KEY_DESCRIPTION).orEmpty(),
            location = result.getString(KEY_LOCATION).orEmpty(),
            colorKey = result.getString(KEY_COLOR) ?: Event.DEFAULT_COLOR,
            rrule = result.getString(KEY_RRULE).orEmpty(),
            reminders = result.getIntArray(KEY_REMINDERS)?.toList().orEmpty()
        )

        /** Identifiant de l'événement modifié, `null` pour une création. */
        fun targetEventId(result: Bundle): String? = result.getString(KEY_EVENT_ID)

        fun targetOccurrenceStart(result: Bundle): Long = result.getLong(KEY_OCCURRENCE_START)

        /** Vrai quand la cible se répète: c'est le seul cas où la portée se demande. */
        fun targetIsSeries(result: Bundle): Boolean = result.getBoolean(KEY_IS_SERIES)
    }
}
