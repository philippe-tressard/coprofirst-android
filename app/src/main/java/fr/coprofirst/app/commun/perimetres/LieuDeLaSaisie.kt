// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.commun.perimetres

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import fr.coprofirst.app.commun.apercu.ApercuServi
import fr.coprofirst.app.commun.formats.FormatsDuSite
import fr.coprofirst.app.commun.lecture.EtiquetteLieu

/**
 * Le lieu d'une sélection en saisie, nommé par le serveur (`POST /perimetres/libelle`) —
 * même le périmètre par défaut —, comme `LieuDeLaSaisie` de l'iOS (#130) : l'épingle et le
 * libellé servi. L'app ne compose jamais ce nom (chemins « › », groupes « · » : la règle du
 * serveur). Sa ligne est réservée avant la première réponse, invisible : rien ne bouge à
 * son arrivée. Rien sans code ; un échec se tait (`EtatApercu`).
 */
@Composable
fun LieuDeLaSaisie(codes: Set<String>, service: ServicePerimetres, repere: String, modifier: Modifier = Modifier) {
    if (codes.isEmpty()) return
    ApercuServi(codes.sorted(), demander = { service.lieu(it) }, modifier) { libelle ->
        val servi = libelle?.takeIf { it.isNotEmpty() }
        if (servi != null) {
            EtiquetteLieu(servi, Modifier.testTag(repere), style = MaterialTheme.typography.bodySmall)
        } else {
            // La place réservée : invisible, et tue au lecteur d'écran.
            Box(Modifier.alpha(0f).clearAndSetSemantics {}) { EtiquetteLieu(FormatsDuSite.TIRET, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
