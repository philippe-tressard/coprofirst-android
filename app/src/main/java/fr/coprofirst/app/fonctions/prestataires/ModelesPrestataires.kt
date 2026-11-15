// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

package fr.coprofirst.app.fonctions.prestataires

import fr.coprofirst.api.modeles.CompteurConfigRead
import fr.coprofirst.api.modeles.ContratRead
import fr.coprofirst.api.modeles.DocumentRead
import fr.coprofirst.api.modeles.EquipementRead
import fr.coprofirst.api.modeles.PrestataireContactLu
import fr.coprofirst.api.modeles.PrestataireRead
import fr.coprofirst.api.modeles.ReleveRead
import fr.coprofirst.app.commun.formats.FormatsDuSite

/** Un contact d'un prestataire ; `nomServi` : le `nom_affiche` servi. */
data class ContactPrestataire(
    val prenom: String? = null,
    val nom: String? = null,
    val fonction: String? = null,
    val telephone: String? = null,
    val email: String? = null,
    val nomServi: String? = null,
) {
    /** « Prénom NOM » servi ; `null` pour un contact sans nom. */
    val nomAffiche: String?
        get() = FormatsDuSite.nom(nomServi, prenom, nom).takeIf { it != FormatsDuSite.TIRET }
}

/** Une fiche de prestataire (`PrestataireRead`). */
data class Prestataire(
    val id: Int,
    val nom: String,
    /** Le code de l'équipement : son libellé est servi par GET /equipements. */
    val specialite: String,
    val type: String? = null,
    val telephone: String? = null,
    val email: String? = null,
    val adresse: String? = null,
    val description: String? = null,
    val contacts: List<ContactPrestataire> = emptyList(),
    /** Les numéros SERVIS, découpés et dédoublonnés (`telephones`) : un lien d'appel chacun. */
    val telephones: List<String> = emptyList(),
)

/** Un contrat d'entretien (`ContratRead`) ; dates en ISO (`AAAA-MM-JJ`). */
data class Contrat(
    val id: Int,
    val prestataireId: Int,
    val libelle: String,
    val typeEquipement: String,
    val dateDebut: String,
    val numeroContrat: String? = null,
    val dureeValeur: Int? = null,
    val dureeUnite: String? = null,
    /** « ↺ Mensuel » : la fréquence en clair, SERVIE (`frequence_libelle`) — vide sans fréquence. */
    val frequenceLibelle: String? = null,
    val prochaineVisite: String? = null,
    val notes: String? = null,
)

/** Un équipement : son code, son libellé et son rang — des données servies, pas une table. */
data class Equipement(val code: String, val libelle: String, val ordre: Int)

/** Un document d'un contrat. */
data class DocumentContrat(val id: Int, val titre: String, val fichierNom: String, val taille: Long? = null)

/** Une catégorie de compteur (`CompteurConfigRead`) et son fournisseur. */
data class Compteur(val id: Int, val typeCompteur: String, val libelle: String, val ordre: Int, val prestataireId: Int? = null)

/** Un relevé de compteur ; `dateReleve` en ISO, `photoUrl` sous `/uploads/`. */
data class Releve(val id: Int, val dateReleve: String, val index: Int? = null, val note: String? = null, val photoUrl: String? = null)

internal fun PrestataireRead.lu() = Prestataire(
    id = id,
    nom = nom,
    specialite = specialite,
    type = typePrestataire?.value,
    telephone = telephone,
    email = email,
    adresse = adresse,
    description = description,
    contacts = contacts.orEmpty().map { it.lu() },
    telephones = telephones,
)

private fun PrestataireContactLu.lu() =
    ContactPrestataire(prenom, nom, fonction, telephone, email, nomAffiche)

internal fun ContratRead.lu() = Contrat(
    id = id,
    prestataireId = prestataireId,
    libelle = libelle,
    typeEquipement = typeEquipement,
    dateDebut = dateDebut.toString(),
    numeroContrat = numeroContrat,
    dureeValeur = dureeInitialeValeur,
    dureeUnite = dureeInitialeUnite,
    frequenceLibelle = frequenceLibelle,
    prochaineVisite = prochaineVisite?.toString(),
    notes = notes,
)

internal fun EquipementRead.lu() = Equipement(code, libelle, ordre)

internal fun DocumentRead.lu() = DocumentContrat(id, titre, fichierNom, tailleOctets?.toLong())

internal fun CompteurConfigRead.lu() = Compteur(id, typeCompteur, label, ordre, prestataireId)

internal fun ReleveRead.lu() = Releve(id, dateReleve.toString(), index, note, photoUrl)
