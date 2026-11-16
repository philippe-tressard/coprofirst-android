// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.notifications.montre

import android.content.Context
import fr.coprofirst.app.fonctions.notifications.push.ReglagesPreferences
import fr.coprofirst.app.fonctions.notifications.push.ReglagesPush
import fr.coprofirst.app.session.ErreurService
import fr.coprofirst.app.session.SessionOuverte

/**
 * La liaison du téléphone à la montre Wear OS (#207, lot 2b), comme l'iPhone relie
 * l'Apple Watch : à l'OUVERTURE d'une résidence, si une montre joignable porte l'app, le
 * téléphone lui obtient un jeton de montre (émis, ou renouvelé s'il en a déjà un) et le lui
 * transmet avec l'adresse et le nom de la résidence ; à la FERMETURE, il le retire du serveur
 * et efface la montre.
 *
 * Abonnée au point d'accroche commun de la session (`AbonneSession`, #150). Liaison éteinte
 * (`LiaisonMontre.ACTIF` de la montre, ici `actif`), ses deux gestes ne font rien.
 *
 * Seul l'identifiant de la montre se garde sur le téléphone (`MontresGardees`), jamais le
 * jeton : il ne fait que passer.
 */
class LiaisonsMontre(
    private val canal: CanalMontre,
    private val service: ServiceMontres,
    private val gardees: MontresGardees,
    private val actif: Boolean,
) {
    suspend fun residenceOuverte(session: SessionOuverte) {
        if (!actif || !canal.montreJointe()) return
        val adresse = session.installation.url
        val jeton = gardees.id(adresse)?.let { id -> renouvele(id, session) } ?: service.emettre(session)
        gardees.garder(adresse, jeton.id)
        canal.transmettre(MessageLiaison.Liaison(adresse, session.installation.nom, jeton.jeton, jeton.expireLe.toString()))
    }

    /**
     * Appelée AVANT l'oubli des cookies (le DELETE part encore authentifié). Sur une session
     * refusée, le retrait peut échouer : la montre s'efface quand même, et le serveur fera
     * tomber le jeton (empreinte du compte, expiration).
     */
    suspend fun residenceFermee(session: SessionOuverte) {
        if (!actif) return
        val adresse = session.installation.url
        val id = gardees.id(adresse) ?: return
        gardees.oublier(adresse)
        runCatching { service.retirer(id, session) }
        canal.transmettre(MessageLiaison.Effacement(adresse))
    }

    companion object {
        /** La liaison de l'app : Data Layer, client généré, préférences privées « montre ». */
        fun de(contexte: Context) = LiaisonsMontre(
            CanalWearable(contexte.applicationContext),
            ServiceMontresApi,
            MontresGardees(ReglagesPreferences(contexte.applicationContext, FICHIER)),
            LiaisonMontre.ACTIF,
        )

        /** Les préférences de la montre, exclues de toute sauvegarde comme le reste. */
        private const val FICHIER = "montre"
    }

    /** Un identifiant gardé que le serveur ne connaît plus (retiré, expiré, purgé) : on en émet un neuf. */
    private suspend fun renouvele(id: Int, session: SessionOuverte): JetonDeMontre? = try {
        service.renouveler(id, session)
    } catch (_: ErreurService.Introuvable) {
        null
    }
}

/** L'identifiant de la montre reliée, par résidence (son adresse) — jamais le jeton. */
class MontresGardees(private val reglages: ReglagesPush) {
    fun id(adresse: String): Int? = reglages.lire(PREFIXE + adresse)?.toIntOrNull()

    fun garder(adresse: String, id: Int) = reglages.ecrire(PREFIXE + adresse, id.toString())

    fun oublier(adresse: String) = reglages.ecrire(PREFIXE + adresse, null)

    private companion object {
        const val PREFIXE = "montre:"
    }
}
