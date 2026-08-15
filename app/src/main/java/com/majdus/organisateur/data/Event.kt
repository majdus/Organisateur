package com.majdus.organisateur.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Un événement de l'agenda.
 *
 * Le stockage est en instants et non plus en date texte: une clé "yyyy-MM-dd" ne sait rien dire
 * d'un événement à cheval sur deux jours, ni d'une série dont la seule ligne en base porte la
 * date de sa *première* occurrence. Le prédicat de lecture est donc partout le chevauchement
 * (`startUtc < finDePlage AND seriesEndUtc > débutDePlage`), jamais un `BETWEEN` sur le début.
 *
 * Deux ancrages cohabitent, décrits dans [com.majdus.organisateur.agenda.Zones]: instant absolu
 * pour un événement à l'heure, minuit UTC pour une journée entière.
 */
@Entity(tableName = "events", indices = [Index("startUtc"), Index("parentId")])
data class Event(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    var title: String,
    /** Début. À l'heure: l'instant réel. Journée entière: minuit UTC du jour, jamais converti. */
    var startUtc: Long,
    /**
     * Fin **exclusive**: un événement de 9 h à 10 h se termine à 10:00:00.000.
     *
     * À ne jamais rendre inclusive: cela décalerait d'une unité tous les tests de chevauchement
     * de l'application, et un événement s'arrêtant à minuit déborderait sur le lendemain.
     */
    var endUtc: Long,
    var allDay: Boolean = false,
    var description: String = "",
    var location: String = "",
    /** Clé de palette, comme `Note.color`: la teinte exacte reste au thème. */
    var colorKey: String = DEFAULT_COLOR,
    /** Règle de répétition au format RRULE iCalendar, "" pour un événement unique. */
    var rrule: String = "",
    /**
     * Fin de la dernière occurrence possible, ou [SERIES_FOREVER] pour une série sans terme.
     *
     * Dérivé de [rrule], donc redondant — mais c'est ce qui permet à SQLite d'écarter une série
     * hors plage sans avoir à la déplier. Vaut [endUtc] pour un événement unique.
     */
    var seriesEndUtc: Long = endUtc,
    /** Occurrence détachée: identifiant de la série mère, "" sinon. */
    var parentId: String = "",
    /** Occurrence détachée: début de l'occurrence qu'elle remplace dans la série. */
    var originalStartUtc: Long = 0,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Série sans date de fin: candidate à toutes les plages, par construction. */
        const val SERIES_FOREVER = Long.MAX_VALUE

        const val DEFAULT_COLOR = "default"
    }
}
