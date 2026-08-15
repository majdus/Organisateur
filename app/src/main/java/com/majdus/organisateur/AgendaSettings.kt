package com.majdus.organisateur

import android.content.Context

/** Découpage affiché par l'agenda. */
enum class AgendaView(val key: String, val daysPerPage: Int) {
    DAY("day", 1),
    WEEK("week", 7),
    MONTH("month", 0),

    /** Liste continue: aucune période fixe, donc aucun jour par page. */
    SCHEDULE("schedule", 0);

    companion object {
        val DEFAULT = MONTH
    }
}

/**
 * Réglages de l'agenda, dans les mêmes préférences que le reste de l'application.
 *
 * Le mode se retient d'une session à l'autre: on ne travaille pas de la même façon selon qu'on
 * suit une journée chargée ou qu'on prépare le mois, et retomber chaque fois sur le même
 * découpage obligerait à le rechoisir.
 */
object AgendaSettings {

    fun view(context: Context): AgendaView {
        val stored = prefs(context).getString(KEY_VIEW, null)
        // Repli sur le défaut pour toute valeur inconnue: une préférence écrite par une version
        // ultérieure ne doit pas rendre l'écran inutilisable.
        return AgendaView.entries.firstOrNull { it.key == stored } ?: AgendaView.DEFAULT
    }

    fun saveView(context: Context, view: AgendaView) {
        prefs(context).edit().putString(KEY_VIEW, view.key).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private const val PREFS_NAME = "organisateur"
    private const val KEY_VIEW = "agenda_view"
}
