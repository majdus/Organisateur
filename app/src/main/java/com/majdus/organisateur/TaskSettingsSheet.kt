package com.majdus.organisateur

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup

/**
 * Réglages de l'écran des tâches.
 *
 * Deux choix entre deux possibilités, donc deux contrôles segmentés: le réglage se lit à sa
 * position, pas à une phrase qui l'expliquerait. La feuille écrit directement dans [TaskSettings]
 * et ne remonte aucun résultat — aucun des deux choix ne change ce qui est déjà affiché,
 * seulement le prochain geste.
 */
class TaskSettingsSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_task_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()

        bind(
            group = view.findViewById(R.id.placementGroup),
            buttons = mapOf(
                R.id.placementTop to NewTaskPlacement.TOP,
                R.id.placementBottom to NewTaskPlacement.BOTTOM
            ),
            current = TaskSettings.placement(context)
        ) { TaskSettings.savePlacement(context, it) }

        bind(
            group = view.findViewById(R.id.checkGroup),
            buttons = mapOf(
                R.id.checkStrike to TaskCheckAction.STRIKE,
                R.id.checkDelete to TaskCheckAction.DELETE
            ),
            current = TaskSettings.checkAction(context)
        ) { TaskSettings.saveCheckAction(context, it) }
    }

    /**
     * Pose la valeur courante sur le groupe, puis enregistre ce que l'utilisateur y choisit.
     *
     * L'écoute est branchée après [MaterialButtonToggleGroup.check]: la sélection initiale
     * réécrirait sinon la préférence qu'elle vient de lire.
     */
    private fun <T> bind(
        group: MaterialButtonToggleGroup,
        buttons: Map<Int, T>,
        current: T,
        onSelect: (T) -> Unit
    ) {
        buttons.entries.first { it.value == current }.let { group.check(it.key) }
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            // Le groupe annonce aussi le segment qui vient d'être désélectionné.
            if (!isChecked) return@addOnButtonCheckedListener
            buttons[checkedId]?.let(onSelect)
        }
    }

    companion object {
        const val TAG = "task_settings_sheet"
    }
}
