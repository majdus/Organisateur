package com.majdus.organisateur

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Une ligne de liste à cocher.
 *
 * [id] n'est pas enregistré: il ne sert qu'à donner une identité stable aux lignes le temps
 * qu'elles sont à l'écran — l'adaptateur en a besoin pour déplacer une ligne plutôt que de la
 * faire clignoter, et pour rendre le focus au bon champ après un changement.
 */
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var checked: Boolean = false
)

/**
 * Lecture, écriture et conversions des listes à cocher.
 *
 * L'ordre de la liste porte deux informations à la fois: l'ordre voulu par l'utilisateur, et la
 * séparation en deux sections — les lignes non cochées d'abord, les cochées ensuite. Cet
 * invariant est celui de Keep, où cocher une ligne l'envoie au bas de la liste; il est maintenu
 * par [normalize] à chaque entrée et sortie.
 */
object Checklist {

    /** Format de stockage: `[{"text":"lait","checked":true}]`, le faux étant implicite. */
    fun serialize(items: List<ChecklistItem>): String {
        val array = JSONArray()
        for (item in items) {
            val json = JSONObject()
            json.put("text", item.text)
            if (item.checked) json.put("checked", true)
            array.put(json)
        }
        return array.toString()
    }

    fun parse(json: String?): MutableList<ChecklistItem> {
        if (json.isNullOrEmpty()) return mutableListOf()
        return read(json)
    }

    /** Lecture d'une liste tronquée par la requête d'aperçu. */
    fun parsePrefix(json: String?): List<ChecklistItem> {
        val complete = TruncatedJson.completeArray(json) ?: return emptyList()
        return read(complete)
    }

    private fun read(json: String): MutableList<ChecklistItem> {
        val items = mutableListOf<ChecklistItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val entry = array.optJSONObject(i) ?: continue
                items += ChecklistItem(
                    text = entry.optString("text"),
                    checked = entry.optBoolean("checked", false)
                )
            }
        } catch (e: Exception) {
            // JSON corrompu: mieux vaut une liste vide qu'un écran de notes inaccessible.
            e.printStackTrace()
        }
        return normalize(items)
    }

    /**
     * Remet les lignes cochées en fin de liste, sans toucher à l'ordre relatif de chaque section.
     */
    fun normalize(items: MutableList<ChecklistItem>): MutableList<ChecklistItem> {
        val sorted = items.filterNot { it.checked } + items.filter { it.checked }
        items.clear()
        items.addAll(sorted)
        return items
    }

    /**
     * Conversion d'un corps de texte en lignes de liste, une par ligne non vide.
     *
     * Les puces déjà saisies à la main sont retirées: une liste écrite « - pain » dans une note
     * de texte ne doit pas se retrouver avec une puce en trop devant sa case à cocher.
     */
    fun fromText(text: CharSequence?): MutableList<ChecklistItem> =
        text?.toString().orEmpty()
            .split('\n')
            .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
            .filter { it.isNotEmpty() }
            .map { ChecklistItem(text = it) }
            .toMutableList()

    /**
     * Conversion inverse: une ligne par élément, cochés compris. Comme dans Keep, l'état des
     * cases est perdu — c'est le texte qui compte une fois les cases retirées.
     */
    fun toText(items: List<ChecklistItem>): String =
        items.map { it.text }.filter { it.isNotBlank() }.joinToString("\n")

    /** Les lignes vides ne sont pas enregistrées: une ligne qu'on n'a pas remplie n'existe pas. */
    fun withoutBlanks(items: List<ChecklistItem>): List<ChecklistItem> =
        items.filter { it.text.isNotBlank() }
}
