package com.majdus.organisateur.agenda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.majdus.organisateur.DateLabels
import com.majdus.organisateur.EventColors
import com.majdus.organisateur.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Grille horaire d'un ou plusieurs jours.
 *
 * Les blocs sont de vraies vues — donc tapotables, lisibles par un lecteur d'écran, et animables
 * — mais les lignes d'heures et de jours sont peintes dans `onDraw`. Vingt-quatre lignes sur sept
 * colonnes feraient cent soixante-huit vues à mesurer et poser à chaque défilement, pour des
 * traits d'un pixel: c'est le principal gain de tenue de l'écran.
 *
 * Aucun recyclage à l'intérieur d'une page: une semaine dépasse rarement cent cinquante blocs, et
 * le pool de vues est réutilisé d'une liaison à l'autre — le même parti que les quarante-deux
 * cellules de la grille du mois, qui tient déjà sans effort.
 */
class TimeGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    /** Appelé au tapotement d'un créneau libre, à l'instant arrondi au quart d'heure. */
    var onSlotClick: ((LocalDateTime) -> Unit)? = null

    /** Appelé au tapotement d'un bloc. */
    var onOccurrenceClick: ((Occurrence) -> Unit)? = null

    var hourHeightPx: Float = resources.displayMetrics.density * 64f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** Jours affichés, de gauche à droite. Un seul en vue jour, sept en vue semaine. */
    var days: List<LocalDate> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val minBlockHeight = density * 28f
    private val blockGap = density * 2f

    private val linePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.border_subtle)
        strokeWidth = density
    }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.accent_calendar)
        strokeWidth = density * 2f
    }

    private val blocks = ArrayList<BlockHolder>()
    private var placed = emptyList<PlacedBlock>()

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, TICK_MS)
        }
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Les blocs sont des vues et interceptent déjà leur propre tapotement: ce qui arrive
            // ici tombe forcément sur un créneau libre.
            val day = dayAt(e.x) ?: return false
            val minutes = ((e.y / hourHeightPx) * 60f).toInt().coerceIn(0, 24 * 60 - 1)
            val snapped = minutes / SNAP_MINUTES * SNAP_MINUTES
            onSlotClick?.invoke(day.atTime(LocalTime.of(snapped / 60, snapped % 60)))
            return true
        }
    })

    init {
        setWillNotDraw(false)
    }

    /** [slotsByDay] suit l'ordre de [days]: une liste de placements par colonne. */
    fun submit(slotsByDay: List<List<Slot>>) {
        val wanted = slotsByDay.sumOf { it.size }
        while (blocks.size < wanted) blocks.add(createBlock())
        for ((index, holder) in blocks.withIndex()) {
            holder.root.visibility = if (index < wanted) View.VISIBLE else View.GONE
        }

        val laid = ArrayList<PlacedBlock>(wanted)
        var cursor = 0
        for ((dayIndex, slots) in slotsByDay.withIndex()) {
            for (slot in slots) {
                bind(blocks[cursor], slot.occurrence)
                laid.add(PlacedBlock(blocks[cursor].root, dayIndex, slot))
                cursor++
            }
        }
        placed = laid
        requestLayout()
    }

    private fun createBlock(): BlockHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_time_block, this, false)
        addView(view)
        return BlockHolder(
            root = view,
            stripe = view.findViewById(R.id.stripe),
            title = view.findViewById(R.id.title),
            subtitle = view.findViewById(R.id.subtitle)
        )
    }

    private fun bind(holder: BlockHolder, occurrence: Occurrence) {
        val color = EventColors.color(context, occurrence.colorKey)
        // Fond très pâle et texte plein: la couleur doit situer l'événement d'un coup d'œil sans
        // rendre son intitulé difficile à lire.
        holder.root.background?.setTint(ColorUtils.setAlphaComponent(color, BACKGROUND_ALPHA))
        holder.stripe.setBackgroundColor(color)
        holder.title.text = occurrence.title
        holder.title.setTextColor(ContextCompat.getColor(context, R.color.text_primary))

        val time = DateLabels.time(occurrence.startUtc)
        holder.subtitle.text =
            if (occurrence.location.isEmpty()) time else "$time · ${occurrence.location}"
        holder.subtitle.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))

        holder.root.contentDescription = context.getString(
            R.string.event_item_description, occurrence.title, time
        )
        holder.root.setOnClickListener { onOccurrenceClick?.invoke(occurrence) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(0, widthMeasureSpec)
        val height = (hourHeightPx * HOURS_PER_DAY).toInt()
        val columnWidth = if (days.isEmpty()) width else width / days.size

        for (block in placed) {
            val slotWidth = (columnWidth / block.slot.columns) - blockGap
            block.view.measure(
                MeasureSpec.makeMeasureSpec(slotWidth.toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(blockHeight(block.slot), MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (days.isEmpty()) return
        val columnWidth = (r - l).toFloat() / days.size

        for (block in placed) {
            val slotWidth = columnWidth / block.slot.columns
            val left = block.dayIndex * columnWidth + block.slot.column * slotWidth
            val top = offsetOf(block.slot, block.dayIndex)
            block.view.layout(
                left.toInt(),
                top.toInt(),
                (left + slotWidth - blockGap).toInt(),
                (top + blockHeight(block.slot)).toInt()
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (hour in 0..HOURS_PER_DAY) {
            val y = hour * hourHeightPx
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
        }
        if (days.size > 1) {
            val columnWidth = width.toFloat() / days.size
            for (index in 1 until days.size) {
                val x = index * columnWidth
                canvas.drawLine(x, 0f, x, height.toFloat(), linePaint)
            }
        }
        drawNowLine(canvas)
    }

    /** Trait de l'heure courante, tracé seulement sur la colonne du jour même. */
    private fun drawNowLine(canvas: Canvas) {
        val today = LocalDate.now()
        val index = days.indexOf(today)
        if (index < 0) return

        val now = LocalTime.now()
        val y = (now.hour + now.minute / 60f) * hourHeightPx
        val columnWidth = width.toFloat() / days.size
        val left = index * columnWidth
        canvas.drawLine(left, y, left + columnWidth, y, nowPaint)
        canvas.drawCircle(left + density * 3f, y, density * 4f, nowPaint)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(tick, TICK_MS)
    }

    override fun onDetachedFromWindow() {
        // Sans ce retrait, la vue reste accrochée à son `Handler` et fuit avec la page qui la
        // porte: le rappel d'une minute la ressusciterait indéfiniment.
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    @Suppress("ClickableViewAccessibility") // Le geste sert à créer, pas à activer la grille.
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun dayAt(x: Float): LocalDate? {
        if (days.isEmpty() || width == 0) return null
        val index = (x / (width.toFloat() / days.size)).toInt()
        return days.getOrNull(index.coerceIn(0, days.size - 1))
    }

    /**
     * Position verticale d'un bloc dans sa colonne.
     *
     * Un événement commencé la veille est ancré en haut de la journée affichée: sa position
     * réelle serait négative, et le bloc sortirait par le haut de la grille.
     */
    private fun offsetOf(slot: Slot, dayIndex: Int): Float {
        val dayStart = Zones.dayStart(days[dayIndex], Zones.current())
        val fromStart = (slot.occurrence.startUtc - dayStart).coerceAtLeast(0L)
        return fromStart / 3_600_000f * hourHeightPx
    }

    private fun blockHeight(slot: Slot): Int {
        val duration = (slot.occurrence.endUtc - slot.occurrence.startUtc).coerceAtLeast(0L)
        val raw = duration / 3_600_000f * hourHeightPx
        return maxOf(raw, minBlockHeight).toInt()
    }

    private class BlockHolder(
        val root: View,
        val stripe: View,
        val title: TextView,
        val subtitle: TextView
    )

    private class PlacedBlock(val view: View, val dayIndex: Int, val slot: Slot)

    private companion object {
        const val HOURS_PER_DAY = 24
        const val SNAP_MINUTES = 15
        const val TICK_MS = 60_000L
        const val BACKGROUND_ALPHA = 38
    }
}
