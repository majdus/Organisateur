package com.majdus.organisateur

import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Réorganisation de la grille des notes au doigt: un appui long soulève la tuile, les autres
 * s'écartent pendant qu'on la promène, et elle se pose là où on la relâche.
 *
 * Quatre directions de déplacement, et aucun balayage: dans une grille, une tuile se déplace
 * aussi bien latéralement que verticalement — contrairement au balayage de [SwipeToDeleteCallback]
 * qui n'a de sens que sur une liste.
 */
class NoteDragCallback(
    private val onMoved: (from: Int, to: Int) -> Unit,
    private val onDragStarted: () -> Unit,
    private val onDragFinished: () -> Unit
) : ItemTouchHelper.Callback() {

    private var isDragging = false

    override fun isLongPressDragEnabled(): Boolean = true

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int = makeMovementFlags(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        0
    )

    /**
     * Plus bas que la moitié retenue par défaut: la place se creuse dès que la tuile mord
     * franchement sur sa voisine, sans qu'il faille la recouvrir. Le geste en paraît plus vif.
     */
    override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        onMoved(from, to)
        // Une brève impulsion à chaque permutation: on sent la grille se réorganiser sous le doigt.
        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || viewHolder == null) return

        isDragging = true
        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        CardLift.raise(viewHolder)
        onDragStarted()
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        CardLift.settle(viewHolder)
        // `clearView` est aussi appelé quand une tuile quitte l'écran en cours de nettoyage:
        // sans ce drapeau, un simple défilement déclencherait une écriture de l'ordre.
        if (!isDragging) return
        isDragging = false
        onDragFinished()
    }
}
