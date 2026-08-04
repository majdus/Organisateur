package com.majdus.organisateur

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Une tuile de l'accueil. [count] est le seul champ qui change après le premier affichage,
 * ce qui permet de ne réanimer que l'étiquette lors d'un retour sur l'écran.
 */
data class HomeTile(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val accentRes: Int,
    @ColorRes val accentSoftRes: Int,
    val count: String = ""
)

class HomeAdapter(
    private val onClick: (HomeTile) -> Unit
) : ListAdapter<HomeTile, HomeAdapter.TileViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_tile, parent, false)
        return TileViewHolder(view)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class TileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val iconTile: View = view.findViewById(R.id.iconTile)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val title: TextView = view.findViewById(R.id.title)
        private val subtitle: TextView = view.findViewById(R.id.subtitle)
        private val count: TextView = view.findViewById(R.id.count)

        fun bind(tile: HomeTile, onClick: (HomeTile) -> Unit) {
            val context = itemView.context
            val accent = ContextCompat.getColor(context, tile.accentRes)
            val accentSoft = ContextCompat.getColor(context, tile.accentSoftRes)

            icon.setImageResource(tile.iconRes)
            icon.imageTintList = ColorStateList.valueOf(accent)
            iconTile.backgroundTintList = ColorStateList.valueOf(accentSoft)

            title.setText(tile.titleRes)
            subtitle.setText(tile.subtitleRes)

            count.text = tile.count
            count.setTextColor(accent)
            count.backgroundTintList = ColorStateList.valueOf(accentSoft)
            count.visibility = if (tile.count.isEmpty()) View.INVISIBLE else View.VISIBLE

            itemView.setOnClickListener { onClick(tile) }
            itemView.contentDescription =
                "${context.getString(tile.titleRes)}, ${tile.count}"
        }
    }

    companion object {
        const val TASKS = "tasks"
        const val NOTES = "notes"
        const val ALARMS = "alarms"
        const val CALENDAR = "calendar"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HomeTile>() {
            override fun areItemsTheSame(oldItem: HomeTile, newItem: HomeTile): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: HomeTile, newItem: HomeTile): Boolean =
                oldItem == newItem
        }
    }
}
