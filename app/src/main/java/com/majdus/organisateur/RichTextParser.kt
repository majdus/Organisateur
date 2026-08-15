package com.majdus.organisateur

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import org.json.JSONArray
import org.json.JSONObject

object RichTextParser {

    /** Une mise en forme de caractères, telle que l'AST la note. */
    data class Style(val bold: Boolean, val italic: Boolean, val color: Int?) {
        val isPlain: Boolean get() = !bold && !italic && color == null
    }

    /**
     * Un corps relu: son texte, et le style qui vaut pour **tout** le texte s'il y en a un.
     *
     * Quand toute la note partage la même mise en forme — le cas d'une note entièrement en gras
     * — [base] la porte et [text] ne contient aucun span. C'est ce qui rend la frappe fluide:
     * voir [parseAstToBody].
     */
    class Body(val text: SpannableStringBuilder, val base: Style?)

    /**
     * Convertit un Android Spanned (venant de l'EditText) en un AST JSON
     * Exemple: [{"text":"Salut","bold":true,"italic":false,"color":null}]
     *
     * [base] est la mise en forme portée par le champ lui-même plutôt que par des spans: elle
     * s'ajoute à celle de chaque tronçon, sans quoi une note entièrement en gras se
     * réenregistrerait sans son gras.
     */
    fun generateAstJsonFromSpannable(spanned: Spanned?, base: Style? = null): String {
        if (spanned.isNullOrEmpty()) return "[]"

        val jsonArray = JSONArray()
        // Le texte est recopié tronçon par tronçon, jamais en entier: sur une note volumineuse,
        // un `toString()` de plus est une copie de plus à faire et à ramasser ensuite.
        val chunk = StringBuilder()
        var chunkBold = false
        var chunkItalic = false
        var chunkColor: Int? = null

        /** Faux tant que le tronçon en cours ne contient que des sauts de ligne. */
        var formatKnown = false

        fun flushChunk() {
            if (chunk.isEmpty()) return
            val jsonObject = JSONObject()
            jsonObject.put("text", chunk.toString())
            jsonObject.put("bold", chunkBold)
            jsonObject.put("italic", chunkItalic)
            if (chunkColor != null) {
                jsonObject.put("color", chunkColor)
            }
            jsonArray.put(jsonObject)
            chunk.setLength(0)
            formatKnown = false
        }

        var i = 0
        while (i < spanned.length) {
            // Trouve le prochain index où un Span commence ou s'arrête. Seuls les deux types
            // qu'on enregistre comptent: chercher toutes les `CharacterStyle` faisait aussi
            // trébucher sur celles du correcteur orthographique, qui en sème une par mot
            // douteux. Chacune coupait le texte en deux tronçons de plus — refusionnés juste
            // après puisque la mise en forme y est la même, mais après avoir alloué pour rien.
            val nextTransition = minOf(
                spanned.nextSpanTransition(i, spanned.length, StyleSpan::class.java),
                spanned.nextSpanTransition(i, spanned.length, ForegroundColorSpan::class.java)
            )

            var isBold = base?.bold ?: false
            var isItalic = base?.italic ?: false
            var color: Int? = base?.color

            for (span in spanned.getSpans(i, nextTransition, StyleSpan::class.java)) {
                if (span.style == android.graphics.Typeface.BOLD || span.style == android.graphics.Typeface.BOLD_ITALIC) isBold = true
                if (span.style == android.graphics.Typeface.ITALIC || span.style == android.graphics.Typeface.BOLD_ITALIC) isItalic = true
            }
            for (span in spanned.getSpans(i, nextTransition, ForegroundColorSpan::class.java)) {
                // S'il y en a plusieurs, le dernier appliqué gagne
                color = span.foregroundColor
            }

            // Deux tronçons voisins portant la même mise en forme n'en font qu'un. Mettre du gras
            // en deux fois, ou rouvrir une note, laisse des spans bout à bout que rien ne
            // distingue: les garder séparés multiplierait les spans à replacer à chaque frappe
            // — le coût de la saisie grandirait au fil des mises en forme posées.
            //
            // Un passage qui n'est fait que de sauts de ligne ne compte pas dans ce départage: il
            // suit ce qui l'entoure au lieu de le couper en deux. Un saut de ligne n'a pas de
            // dessin, gras ou non n'y change rien — mais il perd la mise en forme au moindre
            // remaniement du texte autour de lui, et chacun de ces accidents coupait la note en
            // deux morceaux de plus. Une note s'était ainsi retrouvée en 1 759 tronçons pour 880
            // sauts de ligne dévêtus, donc autant de spans à replacer à chaque frappe.
            if (!isOnlyLineBreaks(spanned, i, nextTransition)) {
                if (formatKnown &&
                    (isBold != chunkBold || isItalic != chunkItalic || color != chunkColor)
                ) {
                    flushChunk()
                }
                chunkBold = isBold
                chunkItalic = isItalic
                chunkColor = color
                formatKnown = true
            }
            chunk.append(spanned, i, nextTransition)

            i = nextTransition
        }
        flushChunk()

        return jsonArray.toString()
    }

    /**
     * Relit un AST volontairement tronqué, tel que renvoyé par la requête d'aperçu de la liste
     * des notes: le JSON s'arrête alors au milieu d'un tronçon. On repart du dernier tronçon
     * complet, ce qui suffit largement pour un aperçu de carte.
     */
    fun parseAstPrefixToSpannable(json: String?): SpannableStringBuilder {
        val complete = TruncatedJson.completeArray(json) ?: return SpannableStringBuilder()
        return parseAstToSpannable(complete)
    }

    /**
     * Convertit l'AST JSON en SpannableStringBuilder pour l'affichage dans l'EditText.
     *
     * Les tronçons voisins portant la même mise en forme sont refondus en une seule suite avant
     * qu'on y pose quoi que ce soit. La sérialisation le fait déjà de son côté, mais la lecture
     * ne peut pas s'y fier: une note écrite par une version antérieure, ou par un chemin qui
     * aurait laissé passer la fragmentation, arrive découpée en centaines de tronçons — et
     * poser un span par tronçon rendait la frappe très lourde, chaque span se replaçant à
     * chaque modification du texte. Refondre ici rend la relecture insensible à la forme reçue,
     * et l'enregistrement suivant remet l'AST d'aplomb en base.
     */
    fun parseAstToSpannable(json: String?): SpannableStringBuilder {
        val body = parseAstToBody(json)
        // Pour les aperçus: le style de base redevient un span, il n'y a pas de champ où le poser.
        body.base?.let { body.text.applyRun(0, body.text.length, it.bold, it.italic, it.color) }
        return body.text
    }

    /**
     * Relit l'AST pour l'éditeur, en distinguant ce qui peut se passer de spans.
     *
     * **Un span qui couvre le document coûte une remise en page complète à chaque frappe.** La
     * raison tient à deux mécanismes du cadre applicatif qui se combinent mal: insérer un
     * caractère décale la position de tous les spans situés après le curseur, et
     * `SpannableStringBuilder` prévient d'un changement pour chacun d'eux; or `StyleSpan` et
     * `ForegroundColorSpan` descendent de `MetricAffectingSpan`, qui implémente `UpdateLayout`,
     * si bien que `DynamicLayout` répond à chacune de ces annonces en remettant en page toute
     * l'étendue du span concerné — deux fois, l'ancienne et la nouvelle.
     *
     * La quantité de texte recalculée est donc toujours la même — tout ce qui suit le curseur —
     * quel que soit le découpage: mesuré sur une note de 37 000 caractères entièrement en gras,
     * un seul span coûtait 105 ms par frappe, dix-neuf en coûtaient 322, mille quatre cent
     * quarante-quatre en coûtaient 4 648. Découper ne réduit rien et multiplie le coût fixe.
     *
     * La seule sortie est de n'avoir aucun span couvrant le document. Quand toute la note
     * partage la même mise en forme — de loin le cas le plus courant d'une note volumineuse —
     * celle-ci n'a rien à faire dans des spans: elle est rendue en [Body.base] et l'éditeur la
     * pose sur le champ de saisie lui-même. Zéro span, donc aucune remise en page au-delà du
     * paragraphe où l'on écrit. Une note réellement bariolée garde ses spans, à raison d'un par
     * passage de mise en forme réelle, et n'en porte donc qu'une poignée.
     */
    fun parseAstToBody(json: String?): Body {
        val builder = SpannableStringBuilder()
        if (json.isNullOrEmpty()) return Body(builder, null)

        // Les suites de même mise en forme, relevées avant qu'on ne pose quoi que ce soit: c'est
        // seulement une fois toutes connues qu'on sait si une seule couvre le texte entier.
        val runs = mutableListOf<Run>()
        try {
            val jsonArray = JSONArray(json)
            var open: Run? = null

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val text = obj.getString("text")
                val style = Style(
                    bold = obj.optBoolean("bold", false),
                    italic = obj.optBoolean("italic", false),
                    color = if (obj.has("color")) obj.getInt("color") else null
                )

                // Même règle qu'à l'écriture: un tronçon fait de seuls sauts de ligne suit ce qui
                // l'entoure au lieu de couper la suite en deux. C'est ce qui remet d'aplomb, dès
                // l'ouverture, une note déjà émiettée en base — sans attendre le prochain
                // enregistrement.
                if (!text.isOnlyLineBreaks()) {
                    if (open != null && open.style != style) {
                        open.end = builder.length
                        open = null
                    }
                    if (open == null) {
                        open = Run(builder.length, builder.length, style).also { runs += it }
                    }
                }
                builder.append(text)
            }
            open?.end = builder.length
        } catch (e: Exception) {
            e.printStackTrace()
            // En cas de JSON corrompu, on fait un fallback propre
        }

        // Une seule suite, non vide, couvrant tout: elle se hisse sur le champ de saisie.
        val only = runs.singleOrNull()
        if (only != null && !only.style.isPlain && only.start == 0 && only.end == builder.length) {
            return Body(builder, only.style)
        }
        for (run in runs) {
            builder.applyRun(run.start, run.end, run.style.bold, run.style.italic, run.style.color)
        }
        return Body(builder, null)
    }

    /** Une suite de texte de mise en forme constante, le temps de la relecture. */
    private class Run(val start: Int, var end: Int, val style: Style)

    /**
     * Pose la mise en forme d'une suite, en un seul span par style.
     *
     * Gras et italique restent deux spans distincts (KISS): cela évite les ennuis de découpe où
     * l'on chercherait à retirer l'italique d'un texte portant le style combiné BOLD_ITALIC.
     *
     * Aucun découpage ici: il a été essayé, mesuré, et il ne sert à rien. Voir [parseAstToBody]
     * — la remise en page déclenchée par une frappe couvre tout ce qui suit le curseur quelle
     * que soit la découpe, et multiplier les spans ne fait que multiplier le coût fixe. Ce qui
     * paie, c'est de ne pas avoir de span du tout sur un style qui vaut pour toute la note.
     */
    private fun SpannableStringBuilder.applyRun(
        start: Int,
        end: Int,
        bold: Boolean,
        italic: Boolean,
        color: Int?
    ) {
        if (end <= start) return
        if (bold) {
            setSpan(
                StyleSpan(android.graphics.Typeface.BOLD),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (italic) {
            setSpan(
                StyleSpan(android.graphics.Typeface.ITALIC),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (color != null) {
            setSpan(ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /**
     * Le passage `[start, end)` n'est-il fait que de sauts de ligne ?
     *
     * Volontairement limité aux seuls sauts de ligne, qui n'ont aucun dessin: une espace en gras,
     * elle, n'a pas tout à fait la même chasse qu'une espace ordinaire, et la faire suivre son
     * voisinage se verrait.
     */
    private fun isOnlyLineBreaks(text: CharSequence, start: Int, end: Int): Boolean {
        if (end <= start) return false
        for (i in start until end) {
            if (text[i] != '\n' && text[i] != '\r') return false
        }
        return true
    }

    private fun String.isOnlyLineBreaks(): Boolean =
        isOnlyLineBreaks(this, 0, length)
}
