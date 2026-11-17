// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.montre.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import fr.coprofirst.montre.ActiviteMontre
import fr.coprofirst.montre.R
import fr.coprofirst.montre.liaison.EtatLecture
import fr.coprofirst.montre.liaison.LectureMontre

/**
 * La complication du cadran, visible de tous (périmètre du 16/11/2026, coprofirst-ios#226) :
 * le logo de CoproFirst, avec un point s'il y a du non lu. Aucun nombre, aucun texte. Elle lit
 * la cloche comme l'app (`LectureMontre`), en ligne ou la dernière gardée ; un toucher ouvre la
 * cloche. Le logo est couvert par sa licence (usage 5, confirmé par Philippe le 16/11/2026).
 */
class ComplicationCloche : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        donnees(request.complicationType, PointNonLu.de(LectureMontre.de(this).lire()))

    override fun getPreviewData(type: ComplicationType): ComplicationData? = donnees(type, point = true)

    private fun donnees(type: ComplicationType, point: Boolean): ComplicationData? {
        val icone = IconeComplication.de(this, point)
        val description = PlainComplicationText.Builder(
            getString(if (point) R.string.complication_non_lu else R.string.complication_rien),
        ).build()
        val ouvrir = PendingIntent.getActivity(
            this, 0, Intent(this, ActiviteMontre::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return when (type) {
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(SmallImage.Builder(icone, SmallImageType.ICON).build(), description)
                    .setTapAction(ouvrir).build()
            ComplicationType.MONOCHROMATIC_IMAGE ->
                MonochromaticImageComplicationData.Builder(MonochromaticImage.Builder(icone).build(), description)
                    .setTapAction(ouvrir).build()
            else -> null
        }
    }
}

/** Le point : du non lu dans la cloche lue (en ligne ou gardée). Rien d'autre ne l'allume. */
object PointNonLu {
    fun de(lecture: EtatLecture): Boolean = when (lecture) {
        is EtatLecture.Lue -> lecture.cloche.nonLues.isNotEmpty()
        is EtatLecture.HorsLigne -> lecture.cloche.nonLues.isNotEmpty()
        else -> false
    }
}
