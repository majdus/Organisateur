package com.majdus.organisateur

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.withTransaction
import com.majdus.organisateur.data.AppDatabase
import com.majdus.organisateur.data.Event
import com.majdus.organisateur.data.EventException
import com.majdus.organisateur.data.EventReminder
import com.majdus.organisateur.data.Note
import com.majdus.organisateur.data.Task
import java.io.InputStream
import java.io.OutputStream

/**
 * Sauvegarde de tout le contenu de l'application dans un fichier JSON, et restauration.
 *
 * Le fichier est écrit et relu **en flux**: une note peut dépasser plusieurs mégaoctets, et la
 * monter en arbre JSON complet doublerait la mémoire nécessaire pour rien.
 *
 * Le format décrit les données et non leur stockage: les rappels y sont un intitulé et une heure,
 * pas la clé `"intitulé\nHH:mm"` des préférences. Une évolution du stockage n'invalide donc pas
 * les sauvegardes déjà faites. Les champs inconnus sont ignorés, les champs absents prennent la
 * valeur par défaut de l'entité: un fichier d'une version voisine se relit sans incident.
 */
object Backup {

    /** Version du format. À n'augmenter que pour un changement qu'une version antérieure lirait mal. */
    private const val FORMAT = 1
    private const val APP = "organisateur"

    /**
     * Réglages repris. Les autres clés des préférences sont de l'état interne (réveils posés,
     * rappels déjà notifiés, migrations faites) qui n'a de sens que sur l'appareil d'origine.
     */
    private val SETTINGS_KEYS = listOf(
        "task_new_placement",
        "task_check_action",
        "agenda_view",
        "calendar_system"
    )

    private const val PREFS_NAME = "organisateur"
    private const val KEY_NOTES_MIGRATED = "notes_migrated"

    /** Le contenu d'une sauvegarde, entièrement relu et validé avant d'écraser quoi que ce soit. */
    class Content(
        val exportedAt: Long,
        val tasks: List<Task>,
        val notes: List<Note>,
        val events: List<Event>,
        val reminders: List<EventReminder>,
        val exceptions: List<EventException>,
        val alarms: List<Alarm>,
        val settings: Map<String, String>
    ) {
        /** Les occurrences détachées sont des lignes à part, mais pas des événements de plus. */
        val eventCount: Int get() = events.count { it.parentId.isEmpty() }
    }

    /** Le fichier n'est pas une sauvegarde lisible. */
    class InvalidBackupException(message: String) : Exception(message)

    /** Sauvegarde écrite par une version ultérieure, dans un format que celle-ci ne connaît pas. */
    class TooRecentBackupException : Exception()

    suspend fun export(context: Context, output: OutputStream) {
        val db = AppDatabase.getDatabase(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        JsonWriter(output.bufferedWriter(Charsets.UTF_8)).use { json ->
            json.setIndent(" ")
            json.beginObject()
            json.name("app").value(APP)
            json.name("format").value(FORMAT.toLong())
            // Pour information seulement: c'est `format` qui décide de la lecture.
            json.name("appVersion").value(
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                    .getOrNull()
            )
            json.name("exportedAt").value(System.currentTimeMillis())

            json.name("tasks").beginArray()
            for (task in db.taskDao().getAllTasks()) {
                json.beginObject()
                json.name("id").value(task.id)
                json.name("title").value(task.title)
                json.name("isCompleted").value(task.isCompleted)
                json.name("timestamp").value(task.timestamp)
                json.name("position").value(task.position.toLong())
                json.endObject()
            }
            json.endArray()

            // Note par note, et chacune relue par tranches: c'est ce qui garde la mémoire bornée
            // à la plus grosse note, et non à la somme de toutes.
            json.name("notes").beginArray()
            for (id in db.noteDao().allIds()) {
                val note = db.noteDao().loadFullNote(id) ?: continue
                json.beginObject()
                json.name("id").value(note.id)
                json.name("title").value(note.title)
                json.name("type").value(note.type)
                json.name("bodyAst").value(note.bodyAst)
                json.name("items").value(note.items)
                json.name("color").value(note.color)
                json.name("position").value(note.position.toLong())
                json.name("createdAt").value(note.createdAt)
                json.name("updatedAt").value(note.updatedAt)
                json.endObject()
            }
            json.endArray()

            json.name("events").beginArray()
            for (event in db.eventDao().all()) {
                json.beginObject()
                json.name("id").value(event.id)
                json.name("title").value(event.title)
                json.name("startUtc").value(event.startUtc)
                json.name("endUtc").value(event.endUtc)
                json.name("allDay").value(event.allDay)
                json.name("description").value(event.description)
                json.name("location").value(event.location)
                json.name("colorKey").value(event.colorKey)
                json.name("rrule").value(event.rrule)
                json.name("seriesEndUtc").value(event.seriesEndUtc)
                json.name("parentId").value(event.parentId)
                json.name("originalStartUtc").value(event.originalStartUtc)
                json.name("createdAt").value(event.createdAt)
                json.name("updatedAt").value(event.updatedAt)
                json.endObject()
            }
            json.endArray()

            json.name("eventReminders").beginArray()
            for (reminder in db.eventReminderDao().all()) {
                json.beginObject()
                json.name("eventId").value(reminder.eventId)
                json.name("minutesBefore").value(reminder.minutesBefore.toLong())
                json.endObject()
            }
            json.endArray()

            json.name("eventExceptions").beginArray()
            for (exception in db.eventExceptionDao().all()) {
                json.beginObject()
                json.name("eventId").value(exception.eventId)
                json.name("originalStartUtc").value(exception.originalStartUtc)
                json.endObject()
            }
            json.endArray()

            json.name("alarms").beginArray()
            for (alarm in AlarmRepository(context).load()) {
                json.beginObject()
                json.name("label").value(alarm.label)
                json.name("hour").value(alarm.hour.toLong())
                json.name("minute").value(alarm.minute.toLong())
                json.name("enabled").value(alarm.enabled)
                json.endObject()
            }
            json.endArray()

            json.name("settings").beginObject()
            for (key in SETTINGS_KEYS) {
                val value = prefs.getString(key, null) ?: continue
                json.name(key).value(value)
            }
            json.endObject()

            json.endObject()
        }
    }

    /**
     * Relit une sauvegarde sans rien écrire. Lève [InvalidBackupException] ou
     * [TooRecentBackupException] si le fichier ne peut pas être restauré.
     */
    fun read(input: InputStream): Content {
        var app: String? = null
        var format: Long? = null
        var exportedAt = 0L
        var tasks = emptyList<Task>()
        var notes = emptyList<Note>()
        var events = emptyList<Event>()
        var reminders = emptyList<EventReminder>()
        var exceptions = emptyList<EventException>()
        var alarms = emptyList<Alarm>()
        var settings = emptyMap<String, String>()

        try {
            JsonReader(input.bufferedReader(Charsets.UTF_8)).use { json ->
                if (json.peek() != JsonToken.BEGIN_OBJECT) throw InvalidBackupException("racine")
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "app" -> app = readScalar(json) as? String
                        "format" -> format = (readScalar(json) as? String)?.toLongOrNull()
                        "exportedAt" -> exportedAt = (readScalar(json) as? String)?.toLongOrNull() ?: 0L
                        // L'en-tête est écrit en premier: inutile de lire des mégaoctets de notes
                        // pour découvrir à la fin que le format est inconnu. Un fichier remanié à
                        // la main peut l'avoir déplacé, d'où la vérification finale en plus.
                        "tasks" -> { checkHeaderIfSeen(app, format); tasks = readArray(json, ::toTask) }
                        "notes" -> { checkHeaderIfSeen(app, format); notes = readArray(json, ::toNote) }
                        "events" -> { checkHeaderIfSeen(app, format); events = readArray(json, ::toEvent) }
                        "eventReminders" -> reminders = readArray(json, ::toReminder)
                        "eventExceptions" -> exceptions = readArray(json, ::toException)
                        "alarms" -> alarms = readArray(json, ::toAlarm)
                        "settings" -> settings = readFields(json)
                            .filterKeys { it in SETTINGS_KEYS }
                            .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
                            .toMap()
                        else -> json.skipValue()
                    }
                }
                json.endObject()
            }
        } catch (e: InvalidBackupException) {
            throw e
        } catch (e: TooRecentBackupException) {
            throw e
        } catch (e: Exception) {
            // JSON malformé, fichier tronqué, type inattendu: pour l'utilisateur c'est la même
            // chose, ce fichier ne se restaure pas.
            throw InvalidBackupException(e.message ?: e.javaClass.simpleName)
        }
        checkHeader(app, format)

        // Dédoublonnage et clés étrangères vérifiés ici plutôt que laissés à SQLite: une seule
        // ligne fautive ferait échouer toute la transaction de restauration.
        val eventIds = events.mapTo(HashSet()) { it.id }
        return Content(
            exportedAt = exportedAt,
            tasks = tasks.distinctBy { it.id },
            notes = notes.distinctBy { it.id },
            events = events.distinctBy { it.id },
            reminders = reminders.filter { it.eventId in eventIds }.distinct(),
            exceptions = exceptions.filter { it.eventId in eventIds }.distinct(),
            alarms = alarms,
            settings = settings
        )
    }

    /**
     * Remplace tout le contenu de l'application par [content].
     *
     * La base est remplacée dans une seule transaction: en cas d'échec, rien n'a bougé. Rappels
     * et réglages ne viennent qu'après, une fois la base acquise.
     */
    suspend fun restore(context: Context, content: Content) {
        val db = AppDatabase.getDatabase(context)
        db.withTransaction {
            db.eventReminderDao().deleteAll()
            db.eventExceptionDao().deleteAll()
            db.eventDao().deleteAll()
            db.taskDao().deleteAll()
            db.noteDao().deleteAll()

            db.taskDao().insertAll(content.tasks)
            for (note in content.notes) db.noteDao().insert(note)
            db.eventDao().insertAll(content.events)
            db.eventReminderDao().insertAll(content.reminders)
            db.eventExceptionDao().insertAll(content.exceptions)
        }

        AlarmRepository(context).replaceAll(content.alarms)

        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (key in SETTINGS_KEYS) {
            val value = content.settings[key]
            if (value != null) editor.putString(key, value) else editor.remove(key)
        }
        // La sauvegarde fait foi: une vieille note unique encore en attente de reprise ne doit
        // pas venir s'ajouter aux notes restaurées.
        editor.putBoolean(KEY_NOTES_MIGRATED, true)
        editor.commit()

        EventAlarmScheduler.rearm(context)
    }

    private fun checkHeaderIfSeen(app: String?, format: Long?) {
        if (app != null || format != null) checkHeader(app, format)
    }

    private fun checkHeader(app: String?, format: Long?) {
        if (app != APP || format == null) throw InvalidBackupException("en-tête")
        if (format > FORMAT) throw TooRecentBackupException()
    }

    private fun <T> readArray(json: JsonReader, convert: (Map<String, Any?>) -> T?): List<T> {
        val result = ArrayList<T>()
        json.beginArray()
        while (json.hasNext()) {
            if (json.peek() == JsonToken.BEGIN_OBJECT) {
                convert(readFields(json))?.let(result::add)
            } else {
                json.skipValue()
            }
        }
        json.endArray()
        return result
    }

    /**
     * Les champs d'un objet à plat. Les nombres sont gardés en texte et convertis à la demande:
     * c'est le seul moyen de relire sans perte un `Long.MAX_VALUE` (série sans fin) ou un nombre
     * écrit autrement qu'attendu.
     */
    private fun readFields(json: JsonReader): Map<String, Any?> {
        if (json.peek() != JsonToken.BEGIN_OBJECT) {
            json.skipValue()
            return emptyMap()
        }
        val fields = HashMap<String, Any?>()
        json.beginObject()
        while (json.hasNext()) {
            val name = json.nextName()
            fields[name] = readScalar(json)
        }
        json.endObject()
        return fields
    }

    private fun readScalar(json: JsonReader): Any? = when (json.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> json.nextString()
        JsonToken.BOOLEAN -> json.nextBoolean()
        JsonToken.NULL -> json.nextNull().let { null }
        else -> json.skipValue().let { null }
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? String)?.toLongOrNull()
    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? String)?.toIntOrNull()
    private fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean

    private fun toTask(f: Map<String, Any?>): Task? {
        val id = f.string("id") ?: return null
        val defaults = Task(id = id, title = "")
        return Task(
            id = id,
            title = f.string("title") ?: "",
            isCompleted = f.bool("isCompleted") ?: defaults.isCompleted,
            timestamp = f.long("timestamp") ?: defaults.timestamp,
            position = f.int("position") ?: defaults.position
        )
    }

    private fun toNote(f: Map<String, Any?>): Note? {
        val id = f.string("id") ?: return null
        val defaults = Note(id = id)
        return Note(
            id = id,
            title = f.string("title") ?: defaults.title,
            bodyAst = f.string("bodyAst") ?: defaults.bodyAst,
            color = f.string("color") ?: defaults.color,
            createdAt = f.long("createdAt") ?: defaults.createdAt,
            updatedAt = f.long("updatedAt") ?: defaults.updatedAt,
            position = f.int("position") ?: defaults.position,
            type = f.string("type") ?: defaults.type,
            items = f.string("items") ?: defaults.items
        )
    }

    /** Un événement sans bornes n'a pas de place dans l'agenda: il est écarté, pas inventé. */
    private fun toEvent(f: Map<String, Any?>): Event? {
        val id = f.string("id") ?: return null
        val start = f.long("startUtc") ?: return null
        val end = f.long("endUtc") ?: return null
        val defaults = Event(id = id, title = "", startUtc = start, endUtc = end)
        return Event(
            id = id,
            title = f.string("title") ?: "",
            startUtc = start,
            endUtc = end,
            allDay = f.bool("allDay") ?: defaults.allDay,
            description = f.string("description") ?: defaults.description,
            location = f.string("location") ?: defaults.location,
            colorKey = f.string("colorKey") ?: defaults.colorKey,
            rrule = f.string("rrule") ?: defaults.rrule,
            seriesEndUtc = f.long("seriesEndUtc") ?: defaults.seriesEndUtc,
            parentId = f.string("parentId") ?: defaults.parentId,
            originalStartUtc = f.long("originalStartUtc") ?: defaults.originalStartUtc,
            createdAt = f.long("createdAt") ?: defaults.createdAt,
            updatedAt = f.long("updatedAt") ?: defaults.updatedAt
        )
    }

    private fun toReminder(f: Map<String, Any?>): EventReminder? {
        val eventId = f.string("eventId") ?: return null
        val minutes = f.int("minutesBefore") ?: return null
        return EventReminder(eventId, minutes)
    }

    private fun toException(f: Map<String, Any?>): EventException? {
        val eventId = f.string("eventId") ?: return null
        val start = f.long("originalStartUtc") ?: return null
        return EventException(eventId, start)
    }

    /** Même tri que pour une saisie: intitulé nettoyé, heure dans les bornes, sinon écarté. */
    private fun toAlarm(f: Map<String, Any?>): Alarm? {
        val label = Alarm.sanitizeLabel(f.string("label") ?: return null)
        val hour = f.int("hour") ?: return null
        val minute = f.int("minute") ?: return null
        if (label.isEmpty() || hour !in 0..23 || minute !in 0..59) return null
        return Alarm(label, hour, minute, f.bool("enabled") ?: true)
    }
}
