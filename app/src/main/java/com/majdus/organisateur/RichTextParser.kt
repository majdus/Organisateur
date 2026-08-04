package com.majdus.organisateur

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import org.json.JSONArray
import org.json.JSONObject

object RichTextParser {

    /**
     * Convertit un Android Spanned (venant de l'EditText) en un AST JSON
     * Exemple: [{"text":"Salut","bold":true,"italic":false,"color":null}]
     */
    fun generateAstJsonFromSpannable(spanned: Spanned?): String {
        if (spanned.isNullOrEmpty()) return "[]"

        val jsonArray = JSONArray()
        val text = spanned.toString()
        var i = 0

        while (i < spanned.length) {
            // Trouve le prochain index où un Span commence ou s'arrête
            val nextTransition = spanned.nextSpanTransition(i, spanned.length, CharacterStyle::class.java)
            
            // Récupère tous les spans actifs pour ce tronçon de texte exact
            val spans = spanned.getSpans(i, nextTransition, CharacterStyle::class.java)
            
            var isBold = false
            var isItalic = false
            var color: Int? = null
            
            for (span in spans) {
                if (span is StyleSpan) {
                    if (span.style == android.graphics.Typeface.BOLD || span.style == android.graphics.Typeface.BOLD_ITALIC) isBold = true
                    if (span.style == android.graphics.Typeface.ITALIC || span.style == android.graphics.Typeface.BOLD_ITALIC) isItalic = true
                }
                if (span is ForegroundColorSpan) {
                    // S'il y en a plusieurs, le dernier appliqué gagne
                    color = span.foregroundColor
                }
            }
            
            val chunkText = text.substring(i, nextTransition)
            
            val jsonObject = JSONObject()
            jsonObject.put("text", chunkText)
            jsonObject.put("bold", isBold)
            jsonObject.put("italic", isItalic)
            if (color != null) {
                jsonObject.put("color", color)
            }
            
            jsonArray.put(jsonObject)
            
            i = nextTransition
        }
        
        return jsonArray.toString()
    }

    /**
     * Relit un AST volontairement tronqué, tel que renvoyé par la requête d'aperçu de la liste
     * des notes: le JSON s'arrête alors au milieu d'un tronçon. On repart du dernier tronçon
     * complet, ce qui suffit largement pour un aperçu de carte.
     */
    fun parseAstPrefixToSpannable(json: String?): SpannableStringBuilder {
        if (json.isNullOrEmpty()) return SpannableStringBuilder()
        val trimmed = json.trimEnd()
        if (trimmed.endsWith("]")) return parseAstToSpannable(trimmed)

        val lastEnd = lastCompleteObjectEnd(trimmed)
        if (lastEnd < 0) return SpannableStringBuilder()
        return parseAstToSpannable(trimmed.substring(0, lastEnd + 1) + "]")
    }

    /**
     * Position de l'accolade fermant le dernier tronçon complet. Le contenu des chaînes est
     * ignoré: une accolade saisie dans le texte de la note ne doit pas passer pour une fin
     * de tronçon.
     */
    private fun lastCompleteObjectEnd(json: String): Int {
        var depth = 0
        var inString = false
        var escaped = false
        var lastEnd = -1
        for (i in json.indices) {
            val character = json[i]
            when {
                escaped -> escaped = false
                inString && character == '\\' -> escaped = true
                character == '"' -> inString = !inString
                inString -> Unit
                character == '{' -> depth++
                character == '}' -> {
                    depth--
                    if (depth == 0) lastEnd = i
                }
            }
        }
        return lastEnd
    }

    /**
     * Convertit l'AST JSON en SpannableStringBuilder pour l'affichage dans l'EditText.
     */
    fun parseAstToSpannable(json: String?): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        if (json.isNullOrEmpty()) return builder
        
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val text = obj.getString("text")
                val isBold = obj.optBoolean("bold", false)
                val isItalic = obj.optBoolean("italic", false)
                val hasColor = obj.has("color")
                val color = if (hasColor) obj.getInt("color") else null
                
                val start = builder.length
                builder.append(text)
                val end = builder.length
                
                // On applique toujours Gras et Italique de manière séparée (KISS)
                // Cela évite les bugs de "Span Splitting" où l'on chercherait à retirer 
                // l'Italique d'un texte qui posséderait le style combiné BOLD_ITALIC.
                if (isBold) {
                    builder.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (isItalic) {
                    builder.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                
                if (color != null) {
                    builder.setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // En cas de JSON corrompu, on fait un fallback propre
        }
        return builder
    }
}
