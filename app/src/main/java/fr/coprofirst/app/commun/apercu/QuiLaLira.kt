// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.apercu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.coprofirst.api.modeles.ApercuLectureEntree
import fr.coprofirst.api.routes.LecturesApi
import fr.coprofirst.app.LocalCouleurs
import fr.coprofirst.app.R
import fr.coprofirst.app.commun.Carte
import fr.coprofirst.app.commun.Libelle
import fr.coprofirst.app.commun.lecture.Lecture
import fr.coprofirst.app.commun.lecture.PastilleLecture
import fr.coprofirst.app.session.SessionOuverte

/**
 * Ce qu'une saisie dit de ses lecteurs (`SaisieLecture` de l'iOS) : sa catégorie et son lieu —
 * sans Destinataires choisis, que l'app ne pose pas ; le serveur dit le reste. Les codes se
 * rangent triés : deux ordres de choix sont la même saisie, et ne se redemandent pas.
 */
data class SaisieLecture private constructor(val categorie: String, val perimetreCible: List<String>) {
    companion object {
        operator fun invoke(categorie: String, perimetreCible: Collection<String> = emptyList()) =
            SaisieLecture(categorie, perimetreCible.sorted())
    }
}

/** « Qui la lirait » : la règle de l'objet enregistré, sur ce que la saisie dit (POST /lectures/apercu). */
interface ServiceLectures {
    suspend fun lecture(saisie: SaisieLecture): Lecture
}

class ServiceLecturesApi(private val session: SessionOuverte) : ServiceLectures {
    override suspend fun lecture(saisie: SaisieLecture): Lecture = session.appeler { base, client ->
        Lecture.depuis(
            LecturesApi(base, client).postLecturesApercu(
                ApercuLectureEntree(categorie = saisie.categorie, perimetreCible = saisie.perimetreCible),
            ).lecture,
        )
    }
}

/**
 * « Qui la lira » d'une affaire en saisie, comme l'iOS (`QuiLaLira`, #179) : la pastille, la
 * phrase entière et l'avertissement que le serveur rend pour CE qui est saisi, par
 * `ApercuServi` (une rafale = une demande ; rien hors ligne). `saisie` nulle — pas encore de
 * catégorie, la règle part d'elle : une ligne qui dit d'où elle viendra, à la même place.
 */
@Composable
fun QuiLaLira(saisie: SaisieLecture?, service: ServiceLectures, repere: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Libelle(stringResource(R.string.apercu_qui_la_lira), Modifier.padding(start = 4.dp).semantics { heading() })
        Carte(Modifier.testTag(repere).semantics(mergeDescendants = true) {}) {
            if (saisie == null) {
                LectureDeLaSaisie(null)
            } else {
                ApercuServi(saisie, demander = { service.lecture(it) }) { LectureDeLaSaisie(it) }
            }
        }
    }
}

@Composable
private fun LectureDeLaSaisie(lecture: Lecture?) {
    val couleurs = LocalCouleurs.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (lecture == null) {
            Text(stringResource(R.string.apercu_selon_la_categorie), style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
        } else {
            PastilleLecture(lecture, memeParDefaut = true)
            Text(lecture.phraseEntiere, style = MaterialTheme.typography.bodyMedium, color = couleurs.textMuted)
            lecture.avertissement?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = couleurs.danger)
            }
        }
    }
}
