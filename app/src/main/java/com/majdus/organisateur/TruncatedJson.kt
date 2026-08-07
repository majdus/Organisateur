package com.majdus.organisateur

/**
 * Réparation d'un tableau JSON coupé en cours de route.
 *
 * Les requêtes d'aperçu de la liste des notes ne lisent qu'un préfixe des colonnes de corps
 * (`substr`), pour ne jamais buter sur la fenêtre de 2 Mo du curseur SQLite. Le JSON qui en
 * revient s'arrête donc au milieu d'un élément. On repart du dernier élément complet: c'est
 * amplement suffisant pour un aperçu de carte, et cela vaut aussi bien pour le texte enrichi
 * que pour une liste à cocher.
 */
object TruncatedJson {

    /**
     * Le plus grand préfixe analysable du tableau, refermé par un crochet. Renvoie null si pas
     * même un élément complet n'a été lu.
     */
    fun completeArray(json: String?): String? {
        if (json.isNullOrEmpty()) return null
        val trimmed = json.trimEnd()
        if (trimmed.endsWith("]")) return trimmed

        val lastEnd = lastCompleteObjectEnd(trimmed)
        if (lastEnd < 0) return null
        return trimmed.substring(0, lastEnd + 1) + "]"
    }

    /**
     * Position de l'accolade fermant le dernier élément complet. Le contenu des chaînes est
     * ignoré: une accolade saisie dans le texte de la note ne doit pas passer pour une fin
     * d'élément.
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
}
