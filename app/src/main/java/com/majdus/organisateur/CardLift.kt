package com.majdus.organisateur

import android.animation.ValueAnimator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * Soulèvement d'une carte saisie au doigt, partagé par les écrans réorganisables.
 *
 * Les cartes de l'application sont plates par choix graphique: c'est l'ombre, apparue le temps
 * du geste, qui détache la carte de ses voisines et rend le déplacement lisible. L'échelle reste
 * discrète — assez pour que la carte paraisse décollée, pas au point de fausser la visée.
 */
object CardLift {

    private const val LIFTED_SCALE = 1.04f
    private const val LIFTED_ELEVATION_DP = 8f
    private const val DURATION_MS = 140L

    private val interpolator = FastOutSlowInInterpolator()

    /** Carte saisie: elle se décolle de la liste. */
    fun raise(viewHolder: RecyclerView.ViewHolder) {
        val density = viewHolder.itemView.resources.displayMetrics.density
        animate(viewHolder, LIFTED_SCALE, LIFTED_ELEVATION_DP * density)
    }

    /**
     * Carte reposée. À appeler depuis `clearView` et non au relâchement du doigt:
     * `ItemTouchHelper` remet lui-même la translation à zéro, mais ni l'échelle ni l'ombre — une
     * vue recyclée ressortirait agrandie et ombrée au milieu de la liste.
     */
    fun settle(viewHolder: RecyclerView.ViewHolder) {
        animate(viewHolder, 1f, 0f)
    }

    /** Échelle et ombre menées ensemble, pour que la carte se soulève et se repose d'un bloc. */
    private fun animate(viewHolder: RecyclerView.ViewHolder, scale: Float, elevation: Float) {
        val card = viewHolder.itemView as? MaterialCardView ?: return
        card.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(DURATION_MS)
            .setInterpolator(interpolator)
            .start()
        ValueAnimator.ofFloat(card.cardElevation, elevation).apply {
            duration = DURATION_MS
            interpolator = CardLift.interpolator
            addUpdateListener { card.cardElevation = it.animatedValue as Float }
            start()
        }
    }
}
