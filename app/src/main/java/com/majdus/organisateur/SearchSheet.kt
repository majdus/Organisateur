package com.majdus.organisateur

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.majdus.organisateur.agenda.Occurrence
import com.majdus.organisateur.agenda.ScheduleAdapter
import com.majdus.organisateur.agenda.Zones
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.EventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Recherche dans l'agenda.
 *
 * Un agenda sans recherche est un agenda qu'on ne relit pas: passé les quelques semaines
 * qu'on parcourt à la main, retrouver un rendez-vous demande de savoir déjà quand il a eu lieu.
 *
 * Les résultats sont des occurrences et non des lignes: une série trouvée rend ses dates, ce qui
 * est précisément ce qu'on cherchait.
 */
class SearchSheet : BottomSheetDialogFragment() {

    private val repository by lazy { EventRepository(AppDatabase.getDatabase(requireContext())) }

    private lateinit var input: TextInputEditText
    private lateinit var results: RecyclerView
    private lateinit var empty: TextView
    private lateinit var adapter: ScheduleAdapter

    /** Recherche en cours: elle est annulée dès la frappe suivante. */
    private var pending: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        input = view.findViewById(R.id.searchInput)
        results = view.findViewById(R.id.searchResults)
        empty = view.findViewById(R.id.searchEmpty)

        adapter = ScheduleAdapter(onClick = { occurrence ->
            setFragmentResult(occurrence)
            dismiss()
        })
        results.adapter = adapter

        input.addTextChangedListener { text -> search(text?.toString().orEmpty()) }
        input.requestFocus()
        dialog?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.let {
            it.state = BottomSheetBehavior.STATE_EXPANDED
            it.skipCollapsed = true
        }
    }

    private fun search(query: String) {
        pending?.cancel()
        if (query.isBlank()) {
            adapter.submitList(emptyList())
            empty.visibility = View.GONE
            return
        }
        pending = lifecycleScope.launch {
            // Une frappe ne déclenche pas une requête: on laisse le temps d'écrire un mot.
            delay(DEBOUNCE_MS)
            val zone = Zones.current()
            val found = withContext(Dispatchers.IO) {
                val today = LocalDate.now(zone)
                repository.search(
                    query,
                    Zones.dayStart(today.minusYears(YEARS_BACK), zone),
                    Zones.dayEnd(today.plusYears(YEARS_AHEAD), zone),
                    zone
                )
            }
            empty.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
            adapter.submitList(ScheduleAdapter.itemsOf(found, zone))
        }
    }

    private fun setFragmentResult(occurrence: Occurrence) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            androidx.core.os.bundleOf(
                KEY_EVENT_ID to occurrence.eventId,
                KEY_START to occurrence.startUtc
            )
        )
    }

    companion object {
        const val REQUEST_KEY = "agenda_search_result"

        private const val KEY_EVENT_ID = "event_id"
        private const val KEY_START = "start"
        private const val DEBOUNCE_MS = 250L

        /**
         * Profondeur de recherche.
         *
         * Bornée parce qu'une série sans fin s'étend indéfiniment: sans limite, chercher un
         * rendez-vous hebdomadaire rendrait une liste que rien n'arrête.
         */
        private const val YEARS_BACK = 3L
        private const val YEARS_AHEAD = 2L

        fun newInstance(): SearchSheet = SearchSheet()

        /** Instant de l'occurrence choisie, pour y amener l'agenda. */
        fun selectedStart(result: Bundle): Long = result.getLong(KEY_START)
    }
}
