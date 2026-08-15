package com.majdus.organisateur

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.majdus.organisateur.agenda.Frequency
import com.majdus.organisateur.agenda.RecurrenceRule
import com.majdus.organisateur.agenda.WeekdayNum
import com.majdus.organisateur.agenda.Zones
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Choix d'une répétition.
 *
 * La feuille travaille sur la règle, pas sur des préréglages: la rangée de fréquences fixe
 * l'unité, le reste l'affine. Un préréglage « toutes les deux semaines » ne serait qu'un chemin
 * plus court vers le même endroit, au prix d'une seconde façon d'exprimer la même chose.
 *
 * Le motif mensuel est le seul cas où l'on ne peut pas deviner: « le 13 » et « le 2e jeudi »
 * tombent sur la même date le mois où l'on choisit, et divergent ensuite. D'où les deux segments.
 */
class RecurrencePickerSheet : BottomSheetDialogFragment() {

    private var frequency: Frequency? = null
    private var interval = 1
    private var weekDays = LinkedHashSet<DayOfWeek>()
    private var byWeekdayPosition = false
    private var until = 0L
    private var count = 0

    /** Jour de référence: c'est lui qui donne les valeurs par défaut du motif. */
    private var anchor: LocalDate = LocalDate.now()

    private lateinit var frequencyGroup: MaterialButtonToggleGroup
    private lateinit var detailsBlock: View
    private lateinit var intervalInput: TextInputEditText
    private lateinit var intervalUnit: TextView
    private lateinit var weekDaysRow: LinearLayout
    private lateinit var monthlyGroup: MaterialButtonToggleGroup
    private lateinit var btnMonthDay: MaterialButton
    private lateinit var btnMonthWeekday: MaterialButton
    private lateinit var endGroup: MaterialButtonToggleGroup
    private lateinit var endDateButton: MaterialButton
    private lateinit var endCountRow: View
    private lateinit var countInput: TextInputEditText

    private val dayCells = LinkedHashMap<DayOfWeek, TextView>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_recurrence, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val arguments = requireArguments()
        anchor = Zones.localDate(arguments.getLong(KEY_ANCHOR), Zones.current())

        frequencyGroup = view.findViewById(R.id.frequencyGroup)
        detailsBlock = view.findViewById(R.id.detailsBlock)
        intervalInput = view.findViewById(R.id.intervalInput)
        intervalUnit = view.findViewById(R.id.intervalUnit)
        weekDaysRow = view.findViewById(R.id.weekDaysRow)
        monthlyGroup = view.findViewById(R.id.monthlyGroup)
        btnMonthDay = view.findViewById(R.id.btnMonthDay)
        btnMonthWeekday = view.findViewById(R.id.btnMonthWeekday)
        endGroup = view.findViewById(R.id.endGroup)
        endDateButton = view.findViewById(R.id.endDateButton)
        endCountRow = view.findViewById(R.id.endCountRow)
        countInput = view.findViewById(R.id.countInput)

        buildWeekDayCells()
        if (savedInstanceState == null) {
            readRule(arguments.getString(KEY_RRULE).orEmpty())
        } else {
            readRule(savedInstanceState.getString(STATE_RRULE).orEmpty())
        }

        frequencyGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            frequency = frequencyOf(checkedId)
            if (frequency == Frequency.WEEKLY && weekDays.isEmpty()) {
                weekDays.add(anchor.dayOfWeek)
            }
            render()
        }
        endGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnEndDate -> {
                    count = 0
                    if (until == 0L) until = defaultUntil()
                }
                R.id.btnEndCount -> {
                    until = 0
                    if (count == 0) count = DEFAULT_COUNT
                }
                else -> {
                    until = 0
                    count = 0
                }
            }
            render()
        }
        monthlyGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            byWeekdayPosition = checkedId == R.id.btnMonthWeekday
        }
        endDateButton.setOnClickListener { showUntilPicker() }

        view.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener { dismiss() }
        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }

        render()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_RRULE, buildRule()?.encode().orEmpty())
    }

    private fun buildWeekDayCells() {
        val inflater = LayoutInflater.from(requireContext())
        for (day in DayOfWeek.entries) {
            val cell = inflater.inflate(R.layout.item_weekday_toggle, weekDaysRow, false) as TextView
            cell.text = RecurrenceLabels.dayInitial(day)
            cell.contentDescription = RecurrenceLabels.dayName(day)
            cell.setOnClickListener {
                // Au moins un jour doit rester coché: une règle hebdomadaire sans jour ne
                // produirait aucune occurrence.
                if (!weekDays.remove(day)) weekDays.add(day)
                if (weekDays.isEmpty()) weekDays.add(day)
                renderWeekDays()
            }
            weekDaysRow.addView(cell)
            dayCells[day] = cell
        }
    }

    private fun readRule(rrule: String) {
        val rule = RecurrenceRule.parse(rrule)
        frequency = rule?.frequency
        interval = rule?.interval ?: 1
        until = rule?.until ?: 0
        count = rule?.count ?: 0
        weekDays = LinkedHashSet(rule?.byDay?.map { it.day }.orEmpty())
        byWeekdayPosition = rule?.byDay?.any { it.position != 0 } == true
        if (frequency == Frequency.WEEKLY && weekDays.isEmpty()) weekDays.add(anchor.dayOfWeek)

        frequencyGroup.check(buttonOf(frequency))
        endGroup.check(
            when {
                until != 0L -> R.id.btnEndDate
                count != 0 -> R.id.btnEndCount
                else -> R.id.btnEndNever
            }
        )
        monthlyGroup.check(if (byWeekdayPosition) R.id.btnMonthWeekday else R.id.btnMonthDay)
        intervalInput.setText(interval.toString())
        countInput.setText((if (count == 0) DEFAULT_COUNT else count).toString())
    }

    private fun render() {
        val frequency = this.frequency
        detailsBlock.visibility = if (frequency == null) View.GONE else View.VISIBLE
        if (frequency == null) return

        intervalUnit.setText(
            when (frequency) {
                Frequency.DAILY -> R.string.recurrence_unit_day
                Frequency.WEEKLY -> R.string.recurrence_unit_week
                Frequency.MONTHLY -> R.string.recurrence_unit_month
                Frequency.YEARLY -> R.string.recurrence_unit_year
            }
        )
        weekDaysRow.visibility = if (frequency == Frequency.WEEKLY) View.VISIBLE else View.GONE
        monthlyGroup.visibility = if (frequency == Frequency.MONTHLY) View.VISIBLE else View.GONE
        if (frequency == Frequency.MONTHLY) {
            btnMonthDay.text = getString(R.string.recurrence_monthly_by_month_day, anchor.dayOfMonth)
            btnMonthWeekday.text = getString(
                R.string.recurrence_monthly_by_weekday,
                RecurrenceLabels.weekdayPosition(requireContext(), anchorPosition(), anchor.dayOfWeek)
            )
        }

        endDateButton.visibility = if (until != 0L) View.VISIBLE else View.GONE
        if (until != 0L) endDateButton.text = DateLabels.absoluteDay(until)
        endCountRow.visibility = if (count != 0) View.VISIBLE else View.GONE

        renderWeekDays()
    }

    private fun renderWeekDays() {
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.white)
        val plainColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        for ((day, cell) in dayCells) {
            val checked = day in weekDays
            cell.background = if (checked) {
                ContextCompat.getDrawable(requireContext(), R.drawable.bg_day_selected)
            } else {
                null
            }
            cell.setTextColor(if (checked) selectedColor else plainColor)
        }
    }

    /**
     * Rang du jour d'ancrage dans son mois, négatif quand c'est le dernier de son espèce.
     *
     * « Le dernier vendredi » se comprend mieux que « le 5e vendredi », et surtout il existe tous
     * les mois — ce que le cinquième ne fait pas.
     */
    private fun anchorPosition(): Int {
        val position = (anchor.dayOfMonth - 1) / 7 + 1
        val isLast = anchor.plusWeeks(1).month != anchor.month
        return if (isLast) -1 else position
    }

    private fun showUntilPicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.recurrence_end)
            .setSelection(until.takeIf { it != 0L } ?: defaultUntil())
            .build()
        picker.addOnPositiveButtonClickListener { selected ->
            // Le sélecteur rend un minuit UTC: la borne est portée en fin de journée pour que
            // l'occurrence de ce jour-là compte encore.
            until = selected + DAY_MS - 1_000L
            render()
        }
        picker.show(childFragmentManager, TAG_DATE_PICKER)
    }

    private fun defaultUntil(): Long =
        Zones.dayEnd(anchor.plusMonths(3), Zones.current()) - 1_000L

    private fun buildRule(): RecurrenceRule? {
        val frequency = this.frequency ?: return null
        val interval = intervalInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 999) ?: 1
        val enteredCount = countInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 999)
            ?: DEFAULT_COUNT

        val byDay = when {
            frequency == Frequency.WEEKLY -> weekDays.sortedBy { it.value }.map { WeekdayNum(0, it) }
            frequency == Frequency.MONTHLY && byWeekdayPosition ->
                listOf(WeekdayNum(anchorPosition(), anchor.dayOfWeek))
            else -> emptyList()
        }
        // Le quantième n'est explicité que sur le motif par jour de semaine: sans BYMONTHDAY, le
        // moteur reprend celui de la série, ce qui revient au même et garde la règle plus courte.
        return RecurrenceRule(
            frequency = frequency,
            interval = interval,
            byDay = byDay,
            count = if (count != 0) enteredCount else 0,
            until = until
        )
    }

    private fun save() {
        val rule = buildRule()
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(KEY_RRULE to rule?.encode().orEmpty())
        )
        dismiss()
    }

    private fun frequencyOf(buttonId: Int): Frequency? = when (buttonId) {
        R.id.btnDaily -> Frequency.DAILY
        R.id.btnWeekly -> Frequency.WEEKLY
        R.id.btnMonthly -> Frequency.MONTHLY
        R.id.btnYearly -> Frequency.YEARLY
        else -> null
    }

    private fun buttonOf(frequency: Frequency?): Int = when (frequency) {
        Frequency.DAILY -> R.id.btnDaily
        Frequency.WEEKLY -> R.id.btnWeekly
        Frequency.MONTHLY -> R.id.btnMonthly
        Frequency.YEARLY -> R.id.btnYearly
        null -> R.id.btnNever
    }

    companion object {
        const val REQUEST_KEY = "recurrence_result"

        private const val KEY_RRULE = "rrule"
        private const val KEY_ANCHOR = "anchor"
        private const val STATE_RRULE = "state_rrule"
        private const val TAG_DATE_PICKER = "recurrence_until_picker"
        private const val DEFAULT_COUNT = 10
        private const val DAY_MS = 86_400_000L

        /** [anchorUtc] est le début de l'événement: il donne le jour et le quantième par défaut. */
        fun newInstance(rrule: String, anchorUtc: Long): RecurrencePickerSheet =
            RecurrencePickerSheet().apply {
                arguments = bundleOf(KEY_RRULE to rrule, KEY_ANCHOR to anchorUtc)
            }

        fun rrule(result: Bundle): String = result.getString(KEY_RRULE).orEmpty()
    }
}
