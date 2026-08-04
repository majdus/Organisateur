package com.majdus.organisateur

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RichTextParserInstrumentedTest {

    @Test
    fun testGenerateAstAndParseBack_Empty() {
        val emptySpannable = SpannableStringBuilder("")
        val ast = RichTextParser.generateAstJsonFromSpannable(emptySpannable)
        assertEquals("[]", ast)

        val parsed = RichTextParser.parseAstToSpannable(ast)
        assertEquals("", parsed.toString())
    }

    @Test
    fun testGenerateAstAndParseBack_SimpleText() {
        val simpleText = SpannableStringBuilder("Hello World")
        val ast = RichTextParser.generateAstJsonFromSpannable(simpleText)
        
        val parsed = RichTextParser.parseAstToSpannable(ast)
        assertEquals("Hello World", parsed.toString())
        
        val spans = parsed.getSpans(0, parsed.length, Any::class.java)
        assertEquals(0, spans.size)
    }

    @Test
    fun testGenerateAstAndParseBack_BoldAndColor() {
        val builder = SpannableStringBuilder("Attention")
        // Applique du gras
        builder.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 9, 0)
        // Applique de la couleur rouge
        builder.setSpan(ForegroundColorSpan(Color.RED), 0, 9, 0)

        val ast = RichTextParser.generateAstJsonFromSpannable(builder)
        assertTrue(ast.contains("bold\":true"))
        assertTrue(ast.contains("color\":${Color.RED}"))

        val parsed = RichTextParser.parseAstToSpannable(ast)
        assertEquals("Attention", parsed.toString())

        val styleSpans = parsed.getSpans(0, parsed.length, StyleSpan::class.java)
        assertEquals(1, styleSpans.size)
        assertEquals(android.graphics.Typeface.BOLD, styleSpans[0].style)

        val colorSpans = parsed.getSpans(0, parsed.length, ForegroundColorSpan::class.java)
        assertEquals(1, colorSpans.size)
        assertEquals(Color.RED, colorSpans[0].foregroundColor)
    }

    @Test
    fun testGenerateAstAndParseBack_MixedFormatting() {
        val builder = SpannableStringBuilder("Gras Normal Rouge")
        // "Gras " -> 0 à 5
        builder.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 4, 0)
        // " Rouge" -> 11 à 17
        builder.setSpan(ForegroundColorSpan(Color.RED), 12, 17, 0)

        val ast = RichTextParser.generateAstJsonFromSpannable(builder)
        
        val parsed = RichTextParser.parseAstToSpannable(ast)
        assertEquals("Gras Normal Rouge", parsed.toString())

        val styleSpans = parsed.getSpans(0, 4, StyleSpan::class.java)
        assertEquals(1, styleSpans.size)
        assertEquals(android.graphics.Typeface.BOLD, styleSpans[0].style)

        val colorSpans = parsed.getSpans(12, 17, ForegroundColorSpan::class.java)
        assertEquals(1, colorSpans.size)
        assertEquals(Color.RED, colorSpans[0].foregroundColor)
    }
}
