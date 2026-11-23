// SPDX-FileCopyrightText: 2026 Philippe TRESSARD
// SPDX-License-Identifier: AGPL-3.0-or-later WITH LicenseRef-CoproFirst-Magasins

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/**
 * Les modules tiers qu'une app EMBARQUE — ceux de sa variante publiée (`release`), sans
 * les modules du dépôt —, un par ligne : `groupe:module`, une tabulation, puis les modules
 * qu'il requiert (séparés par des espaces). Ce graphe dit d'où vient chaque bibliothèque
 * Google (#320) ; la liste, ce que « À propos » doit nommer (#27).
 */
abstract class ListerModulesEmbarques : DefaultTask() {
    @get:Input abstract val racine: Property<ResolvedComponentResult>
    @get:OutputFile abstract val sortie: RegularFileProperty

    @TaskAction
    fun lister() {
        fun nom(c: ResolvedComponentResult) = (c.id as? ModuleComponentIdentifier)?.let { "${it.group}:${it.module}" }
        val requis = sortedMapOf<String, MutableSet<String>>()
        val vus = mutableSetOf<ResolvedComponentResult>()
        val aVoir = ArrayDeque(listOf(racine.get()))
        while (aVoir.isNotEmpty()) {
            val composant = aVoir.removeFirst()
            val enfants = composant.dependencies.filterIsInstance<ResolvedDependencyResult>().map { it.selected }
            nom(composant)?.let { n -> requis.getOrPut(n) { sortedSetOf() } += enfants.mapNotNull(::nom) }
            enfants.filter(vus::add).forEach(aVoir::addLast)
        }
        sortie.get().asFile.writeText(requis.entries.joinToString("") { (m, e) -> "$m\t${e.joinToString(" ")}\n" })
    }
}

/**
 * L'inventaire des composants tiers d'une app (#27, montre #317) : la déclaration COMMUNE
 * aux deux apps (`app/src/main/tiers/composants.json`), confrontée aux modules que la variante
 * publiée embarque — le build s'arrête sur un module non déclaré, sur une bibliothèque Google
 * que la licence ne permet pas (#320) —, puis copiée avec ses textes dans les ressources de
 * l'app (`scripts/generer_composants_tiers.py`). `autreApp` : l'autre app du même magasin,
 * dont la liste dit si un motif de la déclaration sert encore (il peut ne servir qu'à elle).
 */
fun Project.composantsTiers(autreApp: String): TaskProvider<GenererDepuisPython> {
    val lister = tasks.register("listerModulesEmbarques", ListerModulesEmbarques::class.java) {
        racine.set(provider { configurations.getByName("releaseRuntimeClasspath") }.flatMap { it.incoming.resolutionResult.rootComponent })
        sortie.set(layout.buildDirectory.file("modules-embarques.txt"))
    }
    val autre = rootProject.project(autreApp).layout.buildDirectory.file("modules-embarques.txt")
    val generation = generation(
        "genererComposantsTiers", "app/src/main/tiers/composants.json", "scripts/generer_composants_tiers.py",
        lus = listOf("app/src/main/tiers", "LICENSES", "scripts/verifier_dependances_google.py"),
    )
    generation.configure {
        val propre = lister.flatMap { it.sortie }
        lus.from(propre, autre)
        dependsOn("$autreApp:listerModulesEmbarques")
        arguments.add(propre.map { it.asFile.path })
        arguments.add(autre.map { it.asFile.path })
    }
    return generation
}
