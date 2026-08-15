package com.majdus.organisateur

import android.content.Context
import android.icu.util.GregorianCalendar
import android.icu.util.IslamicCalendar
import androidx.annotation.StringRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Système calendaire retenu pour l'écran Agenda.
 *
 * [key] est stable et stockée telle quelle: le libellé peut être réécrit sans toucher aux
 * préférences déjà enregistrées.
 */
enum class CalendarSystem(val key: String, @StringRes val labelRes: Int) {
    GREGORIAN("gregorian", R.string.calendar_system_gregorian),
    HIJRI("hijri", R.string.calendar_system_hijri)
}

/**
 * Tout ce qui dépend du système calendaire, rassemblé ici pour que ni [MonthCalendarView] ni
 * [Agenda] n'aient à en connaître les détails.
 *
 * Le choix ne touche que l'affichage. Un événement reste rangé sous une clé grégorienne
 * "yyyy-MM-dd" (`Event.date`), comparée lexicographiquement par le DAO: convertir le stockage
 * casserait les requêtes par plage. La monnaie d'échange reste donc l'instant en millisecondes,
 * qui ne dépend d'aucun calendrier.
 */
object CalendarSystems {

    /** Le grégorien reste le défaut: c'est le calendrier civil des utilisateurs de l'application. */
    val DEFAULT = CalendarSystem.GREGORIAN

    /** Nom du jour seul: le cycle hebdomadaire est commun aux deux calendriers. */
    private val weekdayOnly = SimpleDateFormat("EEEE", Locale.FRANCE)

    /** Correspondance grégorienne portée par chaque case, en mode hijri. */
    private val gregorianDayMonth = SimpleDateFormat("d/MM", Locale.FRANCE)

    private val gregorianMonthYear = SimpleDateFormat("LLLL yyyy", Locale.FRANCE)
    private val gregorianMonth = SimpleDateFormat("LLLL", Locale.FRANCE)

    /** Comparaison de deux instants au mois près, sans passer par un calendrier. */
    private val gregorianMonthKey = SimpleDateFormat("yyyy-MM", Locale.FRANCE)

    fun current(context: Context): CalendarSystem {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CALENDAR_SYSTEM, null)
        // Repli sur le défaut pour toute valeur inconnue: une préférence corrompue ne doit pas
        // rendre l'écran inutilisable.
        return CalendarSystem.values().firstOrNull { it.key == stored } ?: DEFAULT
    }

    fun save(context: Context, system: CalendarSystem) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CALENDAR_SYSTEM, system.key)
            .apply()
    }

    /**
     * Le calendrier de travail de la grille.
     *
     * `android.icu` plutôt que `java.util`: seule la première famille sait compter en mois
     * hijri, et elle est disponible depuis l'API 24 — bien en deçà du minSdk 28 du projet. Les
     * deux systèmes passent par la même fabrique, donc par le même code de rendu.
     */
    fun newCalendar(system: CalendarSystem): android.icu.util.Calendar = when (system) {
        CalendarSystem.GREGORIAN -> GregorianCalendar()
        // Umm al-Qura: le calendrier civil saoudien, celui qu'Android, iOS et la plupart des
        // applications affichent par défaut. Les variantes purement arithmétiques s'en écartent
        // parfois d'un jour ou deux.
        CalendarSystem.HIJRI -> IslamicCalendar().apply {
            calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        }
    }

    /** "Août 2026" ou "Rajab 1447 H" — en-tête de la grille. */
    fun monthTitle(context: Context, system: CalendarSystem, timestamp: Long): String =
        when (system) {
            // Le chemin grégorien délègue au rendu existant: rien n'est réécrit, donc rien ne
            // peut régresser sur le cas par défaut.
            CalendarSystem.GREGORIAN -> DateLabels.month(timestamp)
            CalendarSystem.HIJRI -> {
                val calendar = newCalendar(system).apply { timeInMillis = timestamp }
                val month = hijriMonth(context, calendar.get(android.icu.util.Calendar.MONTH))
                val year = calendar.get(android.icu.util.Calendar.YEAR)
                "$month $year ${context.getString(R.string.hijri_year_suffix)}"
                    .replaceFirstChar { it.titlecase(Locale.FRANCE) }
            }
        }

    /** "mardi 4 août" ou "mardi 12 rajab 1447 H" — en-tête d'une journée. */
    fun dayTitle(context: Context, system: CalendarSystem, timestamp: Long): String =
        when (system) {
            CalendarSystem.GREGORIAN -> DateLabels.weekday(timestamp)
            CalendarSystem.HIJRI -> {
                val calendar = newCalendar(system).apply { timeInMillis = timestamp }
                val weekday = weekdayOnly.format(Date(timestamp))
                val day = calendar.get(android.icu.util.Calendar.DAY_OF_MONTH)
                val month = hijriMonth(context, calendar.get(android.icu.util.Calendar.MONTH))
                val year = calendar.get(android.icu.util.Calendar.YEAR)
                "$weekday $day $month $year ${context.getString(R.string.hijri_year_suffix)}"
            }
        }

    /**
     * Correspondance grégorienne du jour, ou `null` quand il n'y a rien à rapprocher.
     *
     * Passe par [DateLabels.absoluteDay], qui n'ajoute l'année que si elle sort de l'année en
     * cours: le rappel reste court dans le cas courant.
     */
    fun daySubtitle(system: CalendarSystem, timestamp: Long): String? =
        if (system == CalendarSystem.HIJRI) DateLabels.absoluteDay(timestamp) else null

    /**
     * Journée dite d'un seul tenant: "mardi 12 rajab 1447 H · 4 août". Destinée aux lectures
     * d'écran, où l'on ne peut pas s'appuyer sur la mise en page pour rapprocher deux lignes.
     */
    fun dayHeader(context: Context, system: CalendarSystem, timestamp: Long): String {
        val title = dayTitle(context, system, timestamp)
        val subtitle = daySubtitle(system, timestamp) ?: return title
        return context.getString(R.string.calendar_hijri_day_header, title, subtitle)
    }

    /**
     * Mois grégoriens couverts par le mois affiché, ou `null` quand il n'y a rien à rapprocher.
     *
     * Un mois hijri chevauche presque toujours deux mois grégoriens, et parfois deux années:
     * annoncer « Août – septembre 2026 » sous le titre situe le mois lunaire d'un coup d'œil.
     */
    fun monthSubtitle(
        context: Context,
        system: CalendarSystem,
        startTimestamp: Long,
        endTimestamp: Long
    ): String? {
        if (system == CalendarSystem.GREGORIAN) return null
        val start = Date(startTimestamp)
        val end = Date(endTimestamp)
        val label = when {
            gregorianMonthKey.format(start) == gregorianMonthKey.format(end) ->
                gregorianMonthYear.format(start)
            yearOf(start) == yearOf(end) -> context.getString(
                R.string.calendar_gregorian_span,
                gregorianMonth.format(start),
                gregorianMonthYear.format(end)
            )
            else -> context.getString(
                R.string.calendar_gregorian_span,
                gregorianMonthYear.format(start),
                gregorianMonthYear.format(end)
            )
        }
        return label.replaceFirstChar { it.titlecase(Locale.FRANCE) }
    }

    /** "5/08" — correspondance grégorienne d'une case, en mode hijri. */
    fun gregorianDayLabel(timestamp: Long): String = gregorianDayMonth.format(Date(timestamp))

    private fun yearOf(date: Date): String = gregorianMonthKey.format(date).substringBefore('-')

    /**
     * Nom du mois hijri, pris dans les ressources plutôt que chez le système: rien ne garantit
     * que l'ICU de l'appareil nomme les mois islamiques en français, et un repli sur "M07"
     * s'afficherait tel quel. Les tenir ici, c'est aussi maîtriser la translittération.
     */
    private fun hijriMonth(context: Context, monthIndex: Int): String {
        val months = context.resources.getStringArray(R.array.hijri_months)
        return months.getOrElse(monthIndex) { "" }
    }

    private const val PREFS_NAME = "organisateur"
    private const val KEY_CALENDAR_SYSTEM = "calendar_system"
}
