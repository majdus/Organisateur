package com.majdus.organisateur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray

/**
 * Les aperçus de la grille sont lus sur un préfixe de 4 000 caractères: tout ce qui suit est
 * coupé n'importe où. Ce qui en revient doit rester analysable — et, pour une note volumineuse,
 * doit encore porter du texte à montrer.
 */
class TruncatedJsonTest {

    @Test
    fun `un tableau entier revient tel quel`() {
        val json = """[{"text":"pain","bold":false,"italic":false}]"""
        assertEquals(json, TruncatedJson.completeArray(json))
    }

    @Test
    fun `on repart du dernier element complet`() {
        val json = """[{"text":"pain","bold":false,"italic":false},{"text":"lai"""
        val completed = TruncatedJson.completeArray(json)!!
        val array = JSONArray(completed)
        assertEquals(1, array.length())
        assertEquals("pain", array.getJSONObject(0).getString("text"))
    }

    @Test
    fun `un corps coupe en plein texte garde son debut`() {
        // Le cas ordinaire d'une note un peu fournie: son corps entier ne fait qu'un tronçon,
        // et la coupure tombe donc toujours au milieu de celui-ci.
        val json = """[{"text":"Le début de la note qui continue bien au-delà"""
        val completed = TruncatedJson.completeArray(json)!!
        val array = JSONArray(completed)
        assertEquals(1, array.length())
        assertEquals("Le début de la note qui continue bien au-delà", array.getJSONObject(0).getString("text"))
    }

    @Test
    fun `une coupure sur un echappement ne casse pas la lecture`() {
        // La tranche peut tomber entre la barre oblique et ce qu'elle échappe.
        val cutOnBackslash = """[{"text":"première ligne\"""
        val array = JSONArray(TruncatedJson.completeArray(cutOnBackslash)!!)
        assertEquals("première ligne", array.getJSONObject(0).getString("text"))

        val cutInUnicode = """[{"text":"prix \u20"""
        val second = JSONArray(TruncatedJson.completeArray(cutInUnicode)!!)
        assertEquals("prix ", second.getJSONObject(0).getString("text"))
    }

    @Test
    fun `une coupure juste apres un saut de ligne echappe garde ce saut`() {
        val json = """[{"text":"première ligne\nseconde"""
        val array = JSONArray(TruncatedJson.completeArray(json)!!)
        assertEquals("première ligne\nseconde", array.getJSONObject(0).getString("text"))
    }

    @Test
    fun `une accolade saisie dans la note ne passe pas pour une fin d'element`() {
        val json = """[{"text":"une accolade } dans le texte"""
        val array = JSONArray(TruncatedJson.completeArray(json)!!)
        assertEquals(1, array.length())
        assertTrue(array.getJSONObject(0).getString("text").contains("}"))
    }

    @Test
    fun `rien de lisible ne donne rien`() {
        assertNull(TruncatedJson.completeArray(null))
        assertNull(TruncatedJson.completeArray(""))
        assertNull(TruncatedJson.completeArray("[{"))
    }
}
