package com.majdus.organisateur

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Rangement des tâches par importance: un appui long soulève une carte, les autres s'écartent
 * pendant qu'on la remonte ou la descend, et elle se pose là où on la relâche.
 *
 * Le balayage-suppression de [SwipeToDeleteCallback] est conservé tel quel: les deux gestes ne se
 * marchent pas dessus, l'un naissant d'un appui long et l'autre d'un mouvement latéral. Seul le
 * déplacement vertical est offert — une liste n'a qu'une colonne.
 *
 * [canDropOver] interdit de franchir la frontière entre tâches restantes et tâches terminées:
 * cette séparation est décidée par la case à cocher, pas par le doigt. Sans cela, une tâche
 * lâchée du mauvais côté reviendrait aussitôt à sa place, ce qui se lirait comme un raté.
 */
class TaskReorderCallback(
    context: Context,
    onSwiped: (position: Int) -> Unit,
    private val onMoved: (from: Int, to: Int) -> Unit,
    private val isSameGroup: (from: Int, to: Int) -> Boolean,
    private val onDragStarted: () -> Unit,
    private val onDragFinished: () -> Unit
) : SwipeToDeleteCallback(context, onSwiped) {

    private var isDragging = false

    override fun getDragDirs(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int = ItemTouchHelper.UP or ItemTouchHelper.DOWN

    /** Plus bas que la moitié retenue par défaut: la place se creuse plus tôt, le geste est vif. */
    override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = current.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        return isSameGroup(from, to)
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
        // Une brève impulsion à chaque permutation: on sent la liste se réorganiser sous le doigt.
        viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        return true
    }

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
        // `clearView` conclut aussi un balayage, ou le nettoyage d'une carte sortie de l'écran:
        // sans ce drapeau, supprimer une tâche déclencherait une écriture de l'ordre.
        if (!isDragging) return
        isDragging = false
        onDragFinished()
    }
}
