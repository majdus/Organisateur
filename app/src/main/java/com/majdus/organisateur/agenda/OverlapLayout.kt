package com.majdus.organisateur.agenda

/**
 * Place [occurrence] dans une des [columns] colonnes parallèles d'une grille horaire.
 *
 * La vue en tire directement sa géométrie: largeur `1 / columns`, décalage `column / columns`.
 */
data class Slot(val occurrence: Occurrence, val column: Int, val columns: Int)

/**
 * Répartition des événements qui se chevauchent en colonnes côte à côte, comme le fait n'importe
 * quel agenda en vue jour ou semaine.
 *
 * En deux temps: on isole d'abord les groupes d'événements qui se touchent de proche en proche —
 * les composantes connexes du graphe d'intervalles — puis on distribue chaque groupe en colonnes.
 * Un groupe fixe le nombre de colonnes de tous ses membres, sinon deux blocs voisins n'auraient
 * pas la même largeur et la lecture en souffrirait.
 *
 * Coût: le tri domine, donc `O(n log n)`.
 *
 * N'attend que des événements à l'heure. Les journées entières vivent dans leur propre bandeau,
 * au-dessus de la grille.
 */
object OverlapLayout {

    /**
     * Durée minimale retenue pour le placement.
     *
     * Un rendez-vous de cinq minutes doit occuper sa colonne et rester tapotable: sans plancher,
     * deux événements successifs d'une minute se retrouveraient superposés au pixel près.
     */
    const val MIN_SLOT_MS = 15 * 60_000L

    fun place(occurrences: List<Occurrence>): List<Slot> {
        if (occurrences.isEmpty()) return emptyList()

        // Le plus long d'abord à début égal: il ouvre la colonne de gauche, ce qui donne la
        // lecture attendue — le cadre général à gauche, ce qu'il contient à sa droite.
        val sorted = occurrences.sortedWith(
            compareBy<Occurrence> { it.startUtc }.thenByDescending { it.endUtc }
        )

        val slots = ArrayList<Slot>(sorted.size)
        var group = ArrayList<Occurrence>()
        var groupEnd = Long.MIN_VALUE

        for (occurrence in sorted) {
            if (group.isNotEmpty() && occurrence.startUtc >= groupEnd) {
                slots.addAll(placeGroup(group))
                group = ArrayList()
                groupEnd = Long.MIN_VALUE
            }
            group.add(occurrence)
            groupEnd = maxOf(groupEnd, effectiveEnd(occurrence))
        }
        slots.addAll(placeGroup(group))
        return slots
    }

    /** Glouton: chaque événement prend la première colonne libérée, sinon en ouvre une. */
    private fun placeGroup(group: List<Occurrence>): List<Slot> {
        if (group.isEmpty()) return emptyList()

        val columnEnds = ArrayList<Long>()
        val columnOf = IntArray(group.size)
        for ((index, occurrence) in group.withIndex()) {
            var column = columnEnds.indexOfFirst { it <= occurrence.startUtc }
            if (column < 0) {
                column = columnEnds.size
                columnEnds.add(0L)
            }
            columnEnds[column] = effectiveEnd(occurrence)
            columnOf[index] = column
        }
        return group.mapIndexed { index, occurrence ->
            Slot(occurrence, columnOf[index], columnEnds.size)
        }
    }

    private fun effectiveEnd(occurrence: Occurrence): Long =
        maxOf(occurrence.endUtc, occurrence.startUtc + MIN_SLOT_MS)
}
