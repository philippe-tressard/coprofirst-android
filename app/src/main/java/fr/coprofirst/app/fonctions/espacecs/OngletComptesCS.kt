// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.espacecs

import androidx.compose.runtime.Composable
import fr.coprofirst.app.commun.onglets.ContexteOnglet

/**
 * LA file de travail du conseil, prête à poser dans n'importe quelle page à onglets, comme
 * `OngletComptesCS` de l'iOS : « Comptes & accès » de l'Espace CS et « À traiter » du
 * Paramétrage (#26) l'ouvrent toutes deux. Signature STABLE : elle ne dépend que du
 * contexte de l'onglet (session, avis, actualisation), aucun état du reste de l'Espace CS.
 * Elle montera telle quelle dans la couche commune (#79).
 */
@Composable
fun OngletComptesCS(contexte: ContexteOnglet) = OngletValidations(contexte)
