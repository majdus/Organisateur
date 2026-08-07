package com.majdus.organisateur

import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Déplacement d'une ligne de liste à cocher.
 *
 * Le glisser part de la poignée et d'elle seule ([isLongPressDragEnabled] est désactivé): dans un
 * champ de saisie, l'appui long appartient à la sélection de texte, et le lui prendre rendrait le
 * copier-coller impossible.
 *
 * [canDropOver] refuse la frontière entre éléments cochés et non cochés: cette séparation est
 * décidée par la case, pas par le doigt. Les rangées qui ne sont pas des éléments — la ligne
 * d'ajout, l'en-tête des cochés — ne se déplacent ni ne s'échangent.
 */
class ChecklistDragCallback(
    private val canMove: (position: Int) -> Boolean,
    private val canSwap: (from: Int, to: Int) -> Boolean,
    private val onMoved: (from: Int, to: Int) -> Unit,
    private val onDragFinished: () -> Unit
) : ItemTouchHelper.Callback() {

    private var isDragging = false

    override fun isLongPressDragEnabled(): Boolean = false

    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val position = viewHolder.bindingAdapterPosition
        val dragDirections = if (position != RecyclerView.NO_POSITION && canMove(position)) {
            ItemTouchHelper.UP or ItemTouchHelper.DOWN
        } else {
            0
        }
        return makeMovementFlags(dragDirections, 0)
    }

    override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = current.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        return canSwap(from, to)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        onMoved(from, to)
        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || viewHolder == null) return

        isDragging = true
        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        // La ligne soulevée passe au-dessus des autres, sans ombre portée: le fond de l'écran
        // porte la couleur de la note, une carte flottante y ferait tache.
        viewHolder.itemView.alpha = LIFTED_ALPHA
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1f
        if (!isDragging) return
        isDragging = false
        onDragFinished()
    }

    private companion object {
        const val LIFTED_ALPHA = 0.85f
    }
}
