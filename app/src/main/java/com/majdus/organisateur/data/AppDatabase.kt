package com.majdus.organisateur.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Task::class, Alarm::class, Event::class, Note::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun alarmDao(): AlarmDao
    abstract fun eventDao(): EventDao
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "organisateur_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
