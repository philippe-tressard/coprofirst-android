// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Ce qui se GÉNÈRE au build par un script Python du dépôt, depuis un fichier recopié à
 * l'octet : les palettes (contrat/tables-clients.json → scripts/generer_charte.py), les
 * constantes du serveur (contrat/constantes-clients.json → scripts/generer_constantes.py), les
 * tables des menus — navigation, texte riche, pièces jointes, natures du fil, et celles
 * de chaque menu (→ scripts/generer_tables*.py, découverts par convention), le registre
 * des menus (fonctions/<menu>/Inscription.kt → scripts/generer_registre.py) et le logo au format d'Android
 * (partage-android/src/main/logo/logo.svg → scripts/generer_logo.py).
 * Rien de généré n'est versionné ; aucune couleur ne s'écrit à la main.
 */
abstract class GenererDepuisPython : DefaultTask() {
    /** Ce que le script lit : un fichier, ou les fichiers d'un dossier (les inscriptions). */
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val lus: ConfigurableFileCollection
    /** Le chemin passé au script (le fichier, ou le dossier). */
    @get:Input abstract val entree: Property<String>
    @get:InputFile abstract val generateur: RegularFileProperty
    @get:OutputDirectory abstract val sortie: DirectoryProperty
    /** Ce que le script reçoit après l'entrée et la sortie (la liste des modules embarqués). */
    @get:Input abstract val arguments: ListProperty<String>
    @get:Inject abstract val processus: ExecOperations

    @TaskAction
    fun generer() {
        sortie.get().asFile.deleteRecursively()
        processus.exec {
            commandLine(listOf("python3", "-I", generateur.get().asFile.path, entree.get(), sortie.get().asFile.path) + arguments.get())
        }
    }
}

/**
 * Une génération du module : `entree` (chemin depuis la racine du dépôt) passe au script
 * `generateur`, qui écrit dans `build/generated/<nom>`. `lus` : ce dont le script dépend, s'il
 * lit plus que son entrée.
 */
fun Project.generation(nom: String, entree: String, generateur: String, lus: Any = entree): TaskProvider<GenererDepuisPython> {
    val racine = rootProject.layout.projectDirectory
    val construction = layout.buildDirectory
    return tasks.register(nom, GenererDepuisPython::class.java) {
        this.lus.from(racine.files(lus))
        this.entree.set(racine.file(entree).asFile.path)
        this.generateur.set(racine.file(generateur))
        sortie.set(construction.dir("generated/$nom"))
        arguments.convention(emptyList())
    }
}
