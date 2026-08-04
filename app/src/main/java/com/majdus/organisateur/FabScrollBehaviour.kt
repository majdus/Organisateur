package com.majdus.organisateur

import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

/** Réduit le bouton d'action en icône pendant le défilement vers le bas. */
class FabScrollBehaviour(
    private val fab: ExtendedFloatingActionButton
) : RecyclerView.OnScrollListener() {

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        // En haut de liste — et donc aussi quand la liste ne défile pas — le bouton reprend
        // toujours sa forme étendue: le réduire n'aurait rien masqué.
        when {
            !recyclerView.canScrollVertically(-1) -> if (!fab.isExtended) fab.extend()
            dy > SCROLL_THRESHOLD -> if (fab.isExtended) fab.shrink()
            dy < -SCROLL_THRESHOLD -> if (!fab.isExtended) fab.extend()
        }
    }

    private companion object {
        const val SCROLL_THRESHOLD = 8
    }
}
