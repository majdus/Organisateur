package com.majdus.organisateur

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.min

/**
 * Balayage vers la gauche pour supprimer un rappel. Le fond rouge est dessiné exactement
 * aux dimensions de la carte (l'`itemView` exclut déjà ses marges), avec le même rayon de
 * coin, pour que la révélation reste alignée au design des cartes.
 */
class SwipeToDeleteCallback(
    context: Context,
    private val onSwiped: (position: Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val background: Drawable =
        ContextCompat.getDrawable(context, R.drawable.bg_swipe_delete)!!
    private val icon: Drawable =
        ContextCompat.getDrawable(context, R.drawable.ic_delete_outline)!!.mutate()
    private val density = context.resources.displayMetrics.density
    private val iconSize = (24 * density).toInt()
    private val iconMargin = (24 * density).toInt()

    init {
        icon.setTint(Color.WHITE)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.45f

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) onSwiped(position)
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0f) {
            background.setBounds(itemView.left, itemView.top, itemView.right, itemView.bottom)
            background.draw(canvas)

            // L'icône n'apparaît qu'une fois la carte suffisamment décalée pour la laisser voir.
            val revealed = abs(dX)
            val opacity = min(1f, (revealed - iconMargin) / (iconSize + iconMargin).toFloat())
            if (opacity > 0f) {
                icon.alpha = (opacity * 255).toInt()
                val top = itemView.top + (itemView.height - iconSize) / 2
                icon.setBounds(
                    itemView.right - iconMargin - iconSize,
                    top,
                    itemView.right - iconMargin,
                    top + iconSize
                )
                icon.draw(canvas)
            }
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}
