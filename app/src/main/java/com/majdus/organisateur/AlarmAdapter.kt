package com.majdus.organisateur

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Liste des rappels. [ListAdapter] + [DiffUtil] pour des mises à jour animées et ciblées:
 * basculer un interrupteur ne doit pas reconstruire toute la liste.
 */
class AlarmAdapter(
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onClick: (Alarm) -> Unit
) : ListAdapter<Alarm, AlarmAdapter.AlarmViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        holder.bind(getItem(position), onToggle, onClick)
    }

    override fun onBindViewHolder(
        holder: AlarmViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_COUNTDOWN)) {
            // Rafraîchissement périodique du "dans 8 h 20": on ne retouche que ce texte.
            holder.bindCountdown(getItem(position))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.card)
        private val time: TextView = view.findViewById(R.id.textTime)
        private val label: TextView = view.findViewById(R.id.textLabel)
        private val nextTrigger: TextView = view.findViewById(R.id.textNextTrigger)
        private val chip: View = view.findViewById(R.id.nextTriggerChip)
        private val switch: MaterialSwitch = view.findViewById(R.id.alarmSwitch)

        fun bind(alarm: Alarm, onToggle: (Alarm, Boolean) -> Unit, onClick: (Alarm) -> Unit) {
            time.text = alarm.timeText
            label.text = alarm.label
            bindCountdown(alarm)

            // Un rappel éteint reste lisible mais visiblement en retrait.
            val contentAlpha = if (alarm.enabled) 1f else 0.45f
            time.alpha = contentAlpha
            label.alpha = contentAlpha
            chip.alpha = contentAlpha

            // Le listener est détaché avant setChecked: sans cela, la réutilisation d'une vue
            // recyclée déclencherait un basculement fantôme sur le rappel précédent.
            switch.setOnCheckedChangeListener(null)
            switch.isChecked = alarm.enabled
            switch.setOnCheckedChangeListener { _, isChecked -> onToggle(alarm, isChecked) }

            card.setOnClickListener { onClick(alarm) }
            card.contentDescription = itemView.context.getString(
                R.string.alarm_item_description, alarm.label, alarm.timeText
            )
        }

        fun bindCountdown(alarm: Alarm) {
            nextTrigger.text = AlarmFormatter.nextTrigger(itemView.context, alarm)
        }
    }

    companion object {
        const val PAYLOAD_COUNTDOWN = "countdown"

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Alarm>() {
            override fun areItemsTheSame(oldItem: Alarm, newItem: Alarm): Boolean =
                oldItem.storageKey == newItem.storageKey

            override fun areContentsTheSame(oldItem: Alarm, newItem: Alarm): Boolean =
                oldItem == newItem
        }
    }
}
