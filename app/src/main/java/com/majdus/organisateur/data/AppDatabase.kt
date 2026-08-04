package com.majdus.organisateur.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Task::class, Alarm::class, Event::class, Note::class],
    version = 4,
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "organisateur_database"
                )
                .addMigrations(MIGRATION_3_4)
                // Filet pour les chemins de version imprévus uniquement: toute évolution de
                // schéma doit venir avec sa migration, sinon les données partent.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
