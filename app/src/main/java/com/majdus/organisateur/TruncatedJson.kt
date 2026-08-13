package com.majdus.organisateur

/**
 * Réparation d'un tableau JSON coupé en cours de route.
 *
 * Les requêtes d'aperçu de la liste des notes ne lisent qu'un préfixe des colonnes de corps
 * (`substr`), pour ne jamais buter sur la fenêtre de 2 Mo du curseur SQLite. Le JSON qui en
 * revient s'arrête donc au milieu d'un élément. On repart du dernier élément complet, et à
 * défaut on referme le texte là où il a été coupé: c'est amplement suffisant pour un aperçu de
 * carte, et cela vaut aussi bien pour le texte enrichi que pour une liste à cocher.
 */
object TruncatedJson {

    /**
     * Le plus grand préfixe analysable du tableau, refermé par un crochet. Renvoie null si rien
     * d'exploitable n'a été lu.
     */
    fun completeArray(json: String?): String? {
        if (json.isNullOrEmpty()) return null
        val trimmed = json.trimEnd()
        if (trimmed.endsWith("]")) return trimmed

        val scan = scan(trimmed)
        if (scan.lastObjectEnd >= 0) return trimmed.substring(0, scan.lastObjectEnd + 1) + "]"

        // Pas un seul élément complet: le premier tronçon est à lui seul plus long que la tranche
        // lue. C'est le cas de toute note un peu fournie, dont le corps entier ne fait qu'un
        // tronçon — l'aperçu de ces notes-là serait sinon toujours vide. On referme donc l'élément
        // sur ce qui a été lu du texte. La mise en forme est décrite après lui, et n'a donc pas
        // été lue: le début s'affiche sans elle, ce qu'un aperçu peut se permettre.
        if (scan.depth == 1) {
            if (scan.inValueString && scan.valueEnd >= 0) {
                return trimmed.substring(0, scan.valueEnd) + "\"}]"
            }
            // Coupure retombée juste après le texte, dans ce qui décrit sa mise en forme.
            if (scan.closedValueEnd >= 0) return trimmed.substring(0, scan.closedValueEnd) + "}]"
        }
        return null
    }

    /** Ce qu'un seul parcours du préfixe apprend de sa structure. */
    private class Scan {
        /** Position de l'accolade fermant le dernier élément complet, -1 s'il n'y en a pas. */
        var lastObjectEnd = -1
        var depth = 0

        /** Le préfixe s'arrête-t-il à l'intérieur d'une valeur de chaîne ? */
        var inValueString = false

        /**
         * Fin (exclue) du dernier caractère entièrement lu de cette valeur. Une chaîne ne se
         * referme pas au milieu d'un échappement: `\` seul, ou `\u12`, emporterait le guillemet
         * qu'on ajoute derrière.
         */
        var valueEnd = -1

        /** Fin (exclue) du guillemet fermant de la dernière valeur de chaîne complète. */
        var closedValueEnd = -1
    }

    /**
     * Le contenu des chaînes est ignoré pour la structure: une accolade saisie dans le texte de
     * la note ne doit pas passer pour une fin d'élément.
     */
    private fun scan(json: String): Scan {
        val scan = Scan()
        var inString = false
        var isValue = false
        var escaped = false
        var unicodeLeft = 0
        var previous = ' '

        for (i in json.indices) {
            val character = json[i]
            when {
                unicodeLeft > 0 -> {
                    unicodeLeft--
                    if (unicodeLeft == 0) scan.valueEnd = i + 1
                }
                escaped -> {
                    escaped = false
                    if (character == 'u') unicodeLeft = 4 else scan.valueEnd = i + 1
                }
                inString && character == '\\' -> escaped = true
                character == '"' -> {
                    inString = !inString
                    if (inString) {
                        // Une chaîne qui suit un deux-points est une valeur, pas une clé.
                        isValue = previous == ':'
                        scan.valueEnd = i + 1
                    } else if (isValue) {
                        scan.closedValueEnd = i + 1
                    }
                }
                inString -> scan.valueEnd = i + 1
                character == '{' -> scan.depth++
                character == '}' -> {
                    scan.depth--
                    if (scan.depth == 0) scan.lastObjectEnd = i
                }
            }
            if (!inString && !character.isWhitespace()) previous = character
        }

        scan.inValueString = inString && isValue
        return scan
    }
}
