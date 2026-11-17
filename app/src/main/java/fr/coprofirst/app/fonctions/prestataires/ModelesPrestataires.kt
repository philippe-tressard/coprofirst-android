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
    /** Le nombre d'avis du conseil, SERVI (`nb_notations`). */
    val nbNotations: Int = 0,
    /** La moyenne des avis, SERVIE (`note_moyenne`) ; `null` sans avis. */
    val noteMoyenne: Double? = null,
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
    /** La résiliation et le remplacement servis (contrat ≥ 1.29.0). */
    val remplacement: RemplacementContrat = RemplacementContrat(),
)

/** Le contrat à l'autre bout d'un remplacement (`ContratLie`) : de quoi le nommer et le dater. */
data class ContratLie(
    val id: Int,
    val libelle: String,
    val prestataireNom: String,
    val dateDebut: String,
    val resilieLe: String? = null,
)

/**
 * La résiliation d'un contrat et son remplacement, tels que le serveur les SERT, comme
 * l'iOS (`RemplacementContrat`) : la date, « la résiliation est passée » (`resilie`) et les
 * deux bouts du remplacement — le sens inverse (`remplace`) est déduit par l'API.
 */
data class RemplacementContrat(
    val resilieLe: String? = null,
    val resilie: Boolean = false,
    val remplacePar: ContratLie? = null,
    val remplace: List<ContratLie> = emptyList(),
) {
    /** Une résiliation est datée : la carte ne dit plus « Sans échéance ». */
    val estDatee: Boolean get() = resilieLe != null
}

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
    nbNotations = nbNotations ?: 0,
    noteMoyenne = noteMoyenne?.toDouble(),
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
    remplacement = RemplacementContrat(
        resilieLe = resilieLe?.toString(),
        resilie = resilie ?: false,
        remplacePar = remplacePar?.lu(),
        remplace = remplace.orEmpty().map { it.lu() },
    ),
)

private fun fr.coprofirst.api.modeles.ContratLie.lu() =
    ContratLie(id, libelle, prestataireNom, dateDebut.toString(), resilieLe?.toString())

internal fun EquipementRead.lu() = Equipement(code, libelle, ordre)

internal fun DocumentRead.lu() = DocumentContrat(id, titre, fichierNom, tailleOctets?.toLong())

internal fun CompteurConfigRead.lu() = Compteur(id, typeCompteur, label, ordre, prestataireId)

internal fun ReleveRead.lu() = Releve(id, dateReleve.toString(), index, note, photoUrl)
