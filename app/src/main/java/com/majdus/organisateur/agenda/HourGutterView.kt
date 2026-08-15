package com.majdus.organisateur.agenda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.majdus.organisateur.R

/**
 * Colonne des heures, à gauche d'une grille horaire.
 *
 * Dessinée plutôt que composée de vingt-quatre `TextView`: une colonne d'étiquettes n'a rien à
 * mesurer ni à recycler, et le `onDraw` évite autant de vues à poser à chaque défilement.
 *
 * Elle partage le défilement de la grille en vivant dans le même scroller, donc rien n'est à
 * synchroniser — c'est ce qui rend l'ensemble tenable sans code de coordination.
 */
class HourGutterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Hauteur d'une heure, imposée par la grille voisine pour rester alignée avec elle. */
    var hourHeightPx: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_tertiary)
        textSize = resources.displayMetrics.scaledDensity * 11f
        textAlign = Paint.Align.RIGHT
    }

    private val padding = resources.displayMetrics.density * 8f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(0, widthMeasureSpec),
            (hourHeightPx * HOURS_PER_DAY).toInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        // Minuit n'est pas étiqueté: son libellé serait coupé par le haut de la grille, et la
        // première ligne se lit de toute façon comme le début de la journée.
        for (hour in 1 until HOURS_PER_DAY) {
            val y = hour * hourHeightPx
            canvas.drawText(
                LABELS[hour],
                width - padding,
                y + textPaint.textSize / 3f,
                textPaint
            )
        }
    }

    private companion object {
        const val HOURS_PER_DAY = 24
        val LABELS = Array(HOURS_PER_DAY) { "%02d:00".format(it) }
    }
}
