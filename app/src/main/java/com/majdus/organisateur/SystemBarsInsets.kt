package com.majdus.organisateur

import android.graphics.Rect
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Depuis `targetSdk 35`, Android dessine l'application de bord à bord: le contenu passe sous la
 * barre d'état et sous la barre de navigation, et les attributs de thème `statusBarColor` et
 * `navigationBarColor` ne sont plus honorés. On rend donc leur place aux barres en repoussant le
 * contenu de la hauteur des encarts.
 *
 * Le fond reste tiré de la fenêtre, pas de la vue: c'est lui qu'on aperçoit derrière les barres.
 * Le thème le fixe à `app_background`, et [NoteEditor] le remplace par la couleur de la note pour
 * que l'écran garde sa teinte jusqu'en haut.
 *
 * L'encart du clavier est pris en compte au même titre que les barres — on garde le plus grand des
 * deux, sinon la barre de mise en forme des notes disparaîtrait derrière le clavier.
 */
fun View.padForSystemBars(includeIme: Boolean = false) {
    // Le padding défini dans le XML est la référence: les encarts s'y ajoutent au lieu de
    // l'écraser, et un second passage d'insets ne cumule pas avec le premier.
    val initial = Rect(paddingLeft, paddingTop, paddingRight, paddingBottom)

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val types = if (includeIme) {
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        } else {
            WindowInsetsCompat.Type.systemBars()
        }
        // L'encoche compte aussi: en paysage elle mord sur le côté, où les barres ne sont pas.
        val insets = Insets.max(
            windowInsets.getInsets(types),
            windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout()),
        )

        view.updatePadding(
            left = initial.left + insets.left,
            top = initial.top + insets.top,
            right = initial.right + insets.right,
            bottom = initial.bottom + insets.bottom,
        )
        windowInsets
    }

    // La vue peut être déjà attachée quand on arrive ici (recréation d'activité): sans cette
    // demande explicite, l'écouteur ne serait appelé qu'au prochain changement d'encarts.
    ViewCompat.requestApplyInsets(this)
}
