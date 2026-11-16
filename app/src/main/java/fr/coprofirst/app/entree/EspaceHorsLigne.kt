// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.entree

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import fr.coprofirst.app.commun.EtatHorsLigne
import fr.coprofirst.app.commun.LocalHorsLigne
import java.time.Instant

/**
 * L'espace de la résidence, hors ligne (#28, #158) : l'état posé une fois pour les écrans
 * (`LocalHorsLigne` — le bandeau de chaque page et ses gestes grisés le lisent), et la
 * reprise de la session dès que le réseau revient ou que l'app revient au premier plan
 * pendant une consultation (`repriseEnLigne` de l'iOS).
 */
@Composable
fun EspaceHorsLigne(consultation: Instant?, enLigne: Boolean, reprendre: () -> Unit, contenu: @Composable () -> Unit) {
    LaunchedEffect(consultation != null, enLigne) { if (consultation != null && enLigne) reprendre() }
    LifecycleResumeEffect(consultation, enLigne) {
        if (consultation != null && enLigne) reprendre()
        onPauseOrDispose {}
    }
    CompositionLocalProvider(LocalHorsLigne provides EtatHorsLigne(consultation, enLigne), content = contenu)
}
