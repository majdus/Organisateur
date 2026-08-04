package com.majdus.organisateur

import android.content.Context
import android.text.SpannableStringBuilder
import com.majdus.organisateur.data.Note
import com.majdus.organisateur.data.NoteDao

/**
 * Reprise de la note unique des versions précédentes.
 *
 * Avant les notes multiples, tout était stocké dans les SharedPreferences sous `note` (texte brut)
 * et `note_ast` (mise en forme). Cette note devient la première de la collection, mise en forme
 * comprise. Idempotent: un drapeau garde la trace du passage, sinon chaque ouverture de l'écran
 * recréerait la même note.
 */
object NoteMigration {

    suspend fun migrateLegacyNote(context: Context, noteDao: NoteDao) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_MIGRATED, false)) return

        val legacyAst = preferences.getString(KEY_LEGACY_AST, null)
        val legacyText = preferences.getString(KEY_LEGACY_TEXT, null)

        val bodyAst = when {
            !legacyAst.isNullOrEmpty() && legacyAst != EMPTY_AST -> legacyAst
            // Note antérieure au texte enrichi: seul le texte brut existe.
            !legacyText.isNullOrBlank() ->
                RichTextParser.generateAstJsonFromSpannable(SpannableStringBuilder(legacyText))
            else -> null
        }

        if (bodyAst != null) {
            noteDao.insert(Note(bodyAst = bodyAst))
        }

        // Le drapeau est posé même sans note à reprendre: cela évite de relire ces clés à chaque
        // ouverture. Les anciennes clés sont retirées pour qu'une note supprimée ne réapparaisse
        // pas si le drapeau venait à être perdu.
        preferences.edit()
            .putBoolean(KEY_MIGRATED, true)
            .remove(KEY_LEGACY_AST)
            .remove(KEY_LEGACY_TEXT)
            .apply()
    }

    private const val PREFS_NAME = "organisateur"
    private const val KEY_MIGRATED = "notes_migrated"
    private const val KEY_LEGACY_AST = "note_ast"
    private const val KEY_LEGACY_TEXT = "note"
    private const val EMPTY_AST = "[]"
}
