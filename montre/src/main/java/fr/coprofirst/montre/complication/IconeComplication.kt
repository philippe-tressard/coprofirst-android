// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.complication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import fr.coprofirst.app.charte.Palettes
import fr.coprofirst.app.partage.R as RCommun

/**
 * L'image de la complication : le logo de CoproFirst (généré depuis `logo.svg`, jamais
 * redessiné), et, s'il y a du non lu, un point au coin, de la couleur de danger de la charte
 * (générée). Le cadran teinte l'image monochrome à sa guise : le point y reste un point.
 */
object IconeComplication {
    private const val COTE = 96
    private const val RAYON_POINT = 14f

    fun de(contexte: Context, point: Boolean): Icon {
        val image = createBitmap(COTE, COTE)
        val toile = Canvas(image)
        ContextCompat.getDrawable(contexte, RCommun.drawable.logo)?.apply {
            setBounds(0, 0, COTE, COTE)
            draw(toile)
        }
        if (point) {
            val peinture = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palettes.pour(null).sombre.danger.toArgb() }
            toile.drawCircle(COTE - RAYON_POINT, RAYON_POINT, RAYON_POINT, peinture)
        }
        return Icon.createWithBitmap(image)
    }
}
