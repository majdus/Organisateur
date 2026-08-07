package com.majdus.organisateur

import android.content.Context

/**
 * Où se pose une tâche qui vient d'être créée.
 *
 * [key] est stable et stockée telle quelle: le libellé, lui, vit dans la feuille de réglages et
 * peut être réécrit sans toucher aux préférences déjà enregistrées.
 */
enum class NewTaskPlacement(val key: String) {
    TOP("top"),
    BOTTOM("bottom")
}

/** Ce qu'il advient d'une tâche que l'on coche. */
enum class TaskCheckAction(val key: String) {
    STRIKE("strike"),
    DELETE("delete")
}

/**
 * Les réglages de l'écran des tâches, sur le modèle de [CalendarSystems]: des préférences lues à
 * chaque usage plutôt que retenues en mémoire, il n'y en a pas assez pour que le coût compte.
 *
 * Aucun des deux réglages ne touche aux données déjà en base — ils ne décident que du sort de la
 * prochaine tâche créée et de la prochaine case cochée. En changer d'avis ne défait donc rien.
 */
object TaskSettings {

    /**
     * En haut par défaut: ce qu'on vient de noter est ce qu'on a en tête, et le voir apparaître
     * sous le pouce vaut mieux que devoir faire défiler la liste pour vérifier qu'il est bien là.
     */
    val DEFAULT_PLACEMENT = NewTaskPlacement.TOP

    /** Barrer par défaut: c'est le geste réversible, celui qui ne fait rien perdre. */
    val DEFAULT_CHECK_ACTION = TaskCheckAction.STRIKE

    fun placement(context: Context): NewTaskPlacement =
        // Repli sur le défaut pour toute valeur inconnue: une préférence corrompue ne doit pas
        // rendre l'écran inutilisable.
        NewTaskPlacement.values().firstOrNull { it.key == read(context, KEY_PLACEMENT) }
            ?: DEFAULT_PLACEMENT

    fun savePlacement(context: Context, placement: NewTaskPlacement) =
        write(context, KEY_PLACEMENT, placement.key)

    fun checkAction(context: Context): TaskCheckAction =
        TaskCheckAction.values().firstOrNull { it.key == read(context, KEY_CHECK_ACTION) }
            ?: DEFAULT_CHECK_ACTION

    fun saveCheckAction(context: Context, action: TaskCheckAction) =
        write(context, KEY_CHECK_ACTION, action.key)

    private fun read(context: Context, key: String): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, null)

    private fun write(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    private const val PREFS_NAME = "organisateur"
    private const val KEY_PLACEMENT = "task_new_placement"
    private const val KEY_CHECK_ACTION = "task_check_action"
}
