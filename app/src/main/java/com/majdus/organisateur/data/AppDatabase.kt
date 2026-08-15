package com.majdus.organisateur.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Le schéma est exporté depuis la version 8 (`app/schemas/`), ce qui donne enfin une référence
 * versionnée pour écrire et vérifier les migrations suivantes. Rien n'existe pour les versions
 * antérieures: Room n'exporte que la version que le code déclare.
 */
@Database(
    entities = [
        Task::class,
        Alarm::class,
        Event::class,
        EventReminder::class,
        EventException::class,
        Note::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun alarmDao(): AlarmDao
    abstract fun eventDao(): EventDao
    abstract fun eventReminderDao(): EventReminderDao
    abstract fun eventExceptionDao(): EventExceptionDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Ajout de la table des notes. Sans cette migration explicite, le repli destructif
         * ci-dessous s'appliquerait et emporterait les tâches et les événements déjà saisis.
         * Le schéma doit correspondre exactement à l'entité [Note]: Room le vérifie au démarrage.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (" +
                            "`id` TEXT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`bodyAst` TEXT NOT NULL, " +
                            "`color` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
            }
        }

        /**
         * Ordre manuel des notes. La colonne est remplie d'après le tri qui était appliqué
         * jusqu'ici — la note touchée le plus récemment en tête, départage par identifiant pour
         * ne rien laisser d'indéterminé: à la première ouverture après mise à jour, la grille est
         * exactement dans l'état où l'utilisateur l'a laissée.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `notes` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "UPDATE `notes` SET `position` = (" +
                            "SELECT COUNT(*) FROM `notes` AS other " +
                            "WHERE other.updatedAt > notes.updatedAt " +
                            "OR (other.updatedAt = notes.updatedAt AND other.id < notes.id))"
                )
            }
        }

        /**
         * Ordre manuel des tâches, par importance. Même principe que pour les notes: la colonne
         * est remplie d'après le tri appliqué jusqu'ici — les tâches restantes d'abord, puis les
         * plus anciennes, départage par identifiant — pour que la liste soit inchangée à la
         * première ouverture après mise à jour.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `tasks` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "UPDATE `tasks` SET `position` = (" +
                            "SELECT COUNT(*) FROM `tasks` AS other " +
                            "WHERE other.isCompleted < tasks.isCompleted " +
                            "OR (other.isCompleted = tasks.isCompleted " +
                            "AND other.timestamp < tasks.timestamp) " +
                            "OR (other.isCompleted = tasks.isCompleted " +
                            "AND other.timestamp = tasks.timestamp AND other.id < tasks.id))"
                )
            }
        }

        /**
         * Notes de type liste. Les notes existantes restent du texte, corps intact: les deux
         * colonnes ajoutées prennent la valeur qu'aurait donnée l'entité à une note neuve.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `notes` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'text'"
                )
                database.execSQL(
                    "ALTER TABLE `notes` ADD COLUMN `items` TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        /**
         * L'agenda passe des dates texte aux instants, et gagne rappels et exceptions.
         *
         * Les événements déjà saisis sont repris sans finesse, c'est assumé: aucun d'eux ne
         * portait de durée, donc il n'y a rien à reconstituer. Chacun devient un événement
         * simple d'une heure, sans répétition, borné au jour même — un rendez-vous de 23 h 30
         * s'arrête à minuit plutôt que de déborder sur le lendemain.
         *
         * Le modificateur `'utc'` de SQLite lit la valeur à sa gauche comme une heure locale et
         * la ramène en UTC en appliquant les règles d'heure d'été *de la date concernée*: un
         * événement de janvier et un de juillet ne prennent pas le même décalage, et il n'y a
         * rien à corriger à la main.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `events` RENAME TO `events_v7`")

                // Aucune clause DEFAULT ici: Room compare les valeurs par défaut du schéma à
                // celles déclarées en @ColumnInfo, et un défaut présent d'un seul côté fait
                // échouer la vérification au démarrage. Les défauts vivent dans l'entité Kotlin.
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `events` (" +
                            "`id` TEXT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`startUtc` INTEGER NOT NULL, " +
                            "`endUtc` INTEGER NOT NULL, " +
                            "`allDay` INTEGER NOT NULL, " +
                            "`description` TEXT NOT NULL, " +
                            "`location` TEXT NOT NULL, " +
                            "`colorKey` TEXT NOT NULL, " +
                            "`rrule` TEXT NOT NULL, " +
                            "`seriesEndUtc` INTEGER NOT NULL, " +
                            "`parentId` TEXT NOT NULL, " +
                            "`originalStartUtc` INTEGER NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )

                // Le COALESCE n'est pas là pour sauver l'événement mais le reste de la base: une
                // date illisible donnerait NULL, violerait le NOT NULL et ferait échouer la
                // migration entière — donc emporterait aussi les notes et les tâches par le
                // repli destructif. Elle retombe sur l'instant de création, toujours valide.
                // Deux niveaux d'imbrication sont nécessaires: SQLite ne sait pas réutiliser un
                // alias de colonne dans la liste SELECT qui le définit.
                database.execSQL(
                    "INSERT INTO `events` (`id`, `title`, `startUtc`, `endUtc`, `allDay`, " +
                            "`description`, `location`, `colorKey`, `rrule`, `seriesEndUtc`, " +
                            "`parentId`, `originalStartUtc`, `createdAt`, `updatedAt`) " +
                            "SELECT `id`, `title`, `s`, MIN(`s` + 3600000, `dayEnd`), 0, " +
                            "'', '', 'default', '', MIN(`s` + 3600000, `dayEnd`), '', 0, " +
                            "`timestamp`, `timestamp` FROM (" +
                            "SELECT `id`, `title`, `timestamp`, " +
                            "COALESCE(strftime('%s', `date` || ' ' || " +
                            "printf('%02d:%02d:00', `hour`, `minute`), 'utc') * 1000, " +
                            "`timestamp`) AS `s`, " +
                            "COALESCE(strftime('%s', `date`, '+1 day', 'utc') * 1000, " +
                            "`timestamp` + 3600000) AS `dayEnd` " +
                            "FROM `events_v7`)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_events_startUtc` ON `events` (`startUtc`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_events_parentId` ON `events` (`parentId`)")

                // L'unique notification de la version 7 devient un rappel « à l'heure dite ».
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `event_reminders` (" +
                            "`eventId` TEXT NOT NULL, " +
                            "`minutesBefore` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`eventId`, `minutesBefore`), " +
                            "FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                database.execSQL(
                    "INSERT INTO `event_reminders` (`eventId`, `minutesBefore`) " +
                            "SELECT `id`, 0 FROM `events_v7` WHERE `hasNotification` = 1"
                )

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `event_exceptions` (" +
                            "`eventId` TEXT NOT NULL, " +
                            "`originalStartUtc` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`eventId`, `originalStartUtc`), " +
                            "FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )

                database.execSQL("DROP TABLE `events_v7`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "organisateur_database"
                )
                .addMigrations(
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8
                )
                // Filet pour les chemins de version imprévus uniquement: toute évolution de
                // schéma doit venir avec sa migration, sinon les données partent. Le `false`
                // restreint la casse aux tables gérées par Room, comme le faisait la variante
                // sans argument désormais dépréciée.
                .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
