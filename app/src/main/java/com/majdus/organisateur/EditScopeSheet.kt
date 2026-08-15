package com.majdus.organisateur

import android.content.Context
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.majdus.organisateur.data.EditScope

/**
 * « Cette occurrence, les suivantes, ou toute la série ? »
 *
 * Question posée seulement quand elle se pose — un événement unique n'a qu'une portée possible,
 * et la poser quand même ferait payer à tout le monde le prix des séries.
 *
 * Feuille construite à la volée, sur le modèle du sélecteur de système calendaire: trois lignes,
 * pas de titre explicatif, et le choix ferme la feuille.
 */
object EditScopeSheet {

    fun show(context: Context, isDelete: Boolean, onPick: (EditScope) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.sheet_edit_scope, null)
        val row = view.findViewById<LinearLayout>(R.id.scopeRow)
        val sheet = BottomSheetDialog(context)

        view.findViewById<TextView>(R.id.scopeTitle).setText(
            if (isDelete) R.string.edit_scope_delete_title else R.string.edit_scope_edit_title
        )

        val options = listOf(
            EditScope.THIS_ONE to R.string.edit_scope_this,
            EditScope.THIS_AND_FOLLOWING to R.string.edit_scope_following,
            EditScope.WHOLE_SERIES to R.string.edit_scope_all
        )
        for ((scope, labelRes) in options) {
            val item = LayoutInflater.from(context)
                .inflate(R.layout.item_scope_option, row, false) as TextView
            item.setText(labelRes)
            item.setOnClickListener {
                sheet.dismiss()
                onPick(scope)
            }
            row.addView(item)
        }

        sheet.setContentView(view)
        sheet.show()
    }
}
