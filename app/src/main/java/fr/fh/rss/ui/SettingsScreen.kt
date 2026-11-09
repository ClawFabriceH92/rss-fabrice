package fr.fh.rss.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.fh.rss.BuildConfig
import fr.fh.rss.data.ApercuArchive
import fr.fh.rss.data.Filtre
import fr.fh.rss.data.Reglages
import fr.fh.rss.data.TailleTexte
import fr.fh.rss.data.db.FeedEntity
import fr.fh.rss.update.UpdateManager

/**
 * Paramétrage de l'application, organisé en deux onglets.
 *
 * Le second onglet reprend la liste des flux en erreur : elle était accessible
 * par un écran séparé, il fallait penser à ouvrir le tiroir pour la trouver.
 * Ici elle est à côté des réglages, au même endroit que le reste de la
 * configuration.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EcranReglages(
    vm: MainViewModel,
    reglages: Reglages,
    nombreSources: Int,
    sourcesEnEchec: List<FeedEntity> = emptyList(),
    onglet: Int = 0,
    onOnglet: (Int) -> Unit = {},
    modelesOllama: List<String> = emptyList(),
    etatOllama: String? = null,
    /** Suppression de sources : la confirmation est affichée par l'écran principal. */
    onDemanderSuppression: (List<Long>) -> Unit = {},
) {
    val contexte = LocalContext.current
    val portee = androidx.compose.runtime.rememberCoroutineScope()
    // Suggestions de sources : les états vivent dans le ViewModel, l'écran ne
    // fait que les afficher (une demande survit à une rotation).
    val suggestions by vm.suggestions.collectAsState()
    val suggestionsEnCours by vm.suggestionsEnCours.collectAsState()
    val suggestionsErreur by vm.suggestionsErreur.collectAsState()
    val catalogueSources by vm.catalogueSources.collectAsState()
    val themeCatalogue by vm.themeCatalogue.collectAsState()
    val nettoyage by vm.nettoyage.collectAsState()
    val nettoyageEnCours by vm.nettoyageEnCours.collectAsState()
    // Réduction d'archive en attente de confirmation.
    var reductionEnAttente by remember { androidx.compose.runtime.mutableStateOf<ApercuArchive?>(null) }
    var urlSaisie by remember(reglages.ollamaUrl) {
        androidx.compose.runtime.mutableStateOf(reglages.ollamaUrl)
    }

    val selecteurOpml = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // Lecture hors du thread principal et erreur signalée : voir le ViewModel.
        if (uri != null) vm.importerOpmlDepuis(uri)
    }

    // Pas de Scaffold ni de barre ici : celle de l'écran principal porte déjà le
    // titre « Réglages » et la flèche de retour (sinon le mot s'affiche deux fois).
    Column(Modifier.fillMaxWidth()) {
        ScrollableTabRow(selectedTabIndex = onglet, edgePadding = 0.dp) {
            Tab(
                selected = onglet == 0,
                onClick = { onOnglet(0) },
                text = { Text("Réglages") },
            )
            Tab(
                selected = onglet == 1,
                onClick = { onOnglet(1) },
                text = {
                    Text(
                        if (sourcesEnEchec.isEmpty()) "Flux en erreur"
                        else "Flux en erreur (${sourcesEnEchec.size})",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
            )
            Tab(
                selected = onglet == 2,
                onClick = { onOnglet(2) },
                text = { Text("Suggestions", maxLines = 1) },
            )
            Tab(
                selected = onglet == 3,
                onClick = { onOnglet(3) },
                text = {
                    Text(
                        if (nettoyage.isEmpty()) "Nettoyage" else "Nettoyage (${nettoyage.size})",
                        maxLines = 1,
                    )
                },
            )
        }

        if (onglet == 1) {
            Column(Modifier.fillMaxWidth().weight(1f)) {
                EcranSourcesEnEchec(
                    sources = sourcesEnEchec,
                    onReessayer = { vm.reessayerSource(it) },
                    onReessayerTout = { ids -> vm.reessayerToutesLesSources(ids) },
                    onSupprimer = { onDemanderSuppression(listOf(it)) },
                    onSupprimerTout = { ids -> onDemanderSuppression(ids) },
                )
            }
            return@Column
        }

        if (onglet == 3) {
            Column(Modifier.fillMaxWidth().weight(1f)) {
                EcranNettoyage(
                    propositions = nettoyage,
                    enCours = nettoyageEnCours,
                    onAnalyser = { vm.analyserNettoyage() },
                    onSupprimer = { ids -> onDemanderSuppression(ids) },
                )
            }
            return@Column
        }

        if (onglet == 2) {
            Column(Modifier.fillMaxWidth().weight(1f)) {
                EcranSuggestions(
                    catalogue = catalogueSources,
                    themes = vm.themesCatalogue,
                    theme = themeCatalogue,
                    onTheme = { vm.setThemeCatalogue(it) },
                    propositions = suggestions,
                    enCours = suggestionsEnCours,
                    erreur = suggestionsErreur,
                    modele = reglages.ollamaModele,
                    onAjouterEntree = { entree ->
                        vm.ajouterSuggestion(
                            fr.fh.rss.data.net.SuggestionsSources.Proposition(
                                nom = entree.nom,
                                url = entree.url,
                                etat = fr.fh.rss.data.net.SuggestionsSources.Etat.PROPOSE,
                                detail = "catalogue · ${entree.theme}",
                            ),
                        )
                    },
                    onChoisirDansCatalogue = { vm.choisirDansCatalogue() },
                    onChercherAilleurs = { vm.suggererSources() },
                    onAjouter = { vm.ajouterSuggestion(it) },
                    onAjouterTout = { liste -> liste.forEach { vm.ajouterSuggestion(it) } },
                    onEffacer = { vm.effacerSuggestions() },
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {

            item { Titre("Collecte") }

            item {
                LigneBascule(
                    titre = "Collecte périodique",
                    detail = "Android impose au minimum 15 minutes entre deux passages.",
                    actif = reglages.collectePeriodique,
                ) { vm.setCollectePeriodique(it) }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Fréquence", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((minutes, libelle) in intervalles) {
                            FilterChip(
                                selected = reglages.intervalleMinutes == minutes,
                                onClick = { vm.setIntervalle(minutes) },
                                label = { Text(libelle) },
                                enabled = reglages.collectePeriodique,
                            )
                        }
                    }
                }
            }

            item {
                LigneBascule(
                    titre = "Wi-Fi uniquement",
                    detail = "La collecte attend un réseau non facturé.",
                    actif = reglages.wifiUniquement,
                ) { vm.setWifiUniquement(it) }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Titre("Notifications") }

            item {
                // Sans collecte de fond, rien ne peut partir : l'option est grisée et le dit.
                LigneBascule(
                    titre = "Nouveaux articles",
                    detail = if (reglages.collectePeriodique) {
                        "Une notification après une collecte qui rapporte du neuf " +
                            "(3 titres en aperçu)."
                    } else {
                        "Inactif : active la collecte périodique ci-dessus pour être notifié."
                    },
                    actif = reglages.notifierNouveaux,
                    modifiable = reglages.collectePeriodique,
                ) { vm.setNotifierNouveaux(it) }
            }

            item {
                LigneBascule(
                    titre = "Silence la nuit",
                    detail = "Aucune notification entre 22 h et 7 h : les articles reçus " +
                        "la nuit sont annoncés à la première collecte du matin.",
                    actif = reglages.silenceNuit,
                    modifiable = reglages.collectePeriodique,
                ) { vm.setSilenceNuit(it) }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Titre("Affichage") }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Vue de la liste", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((valeur, libelle) in vues) {
                            FilterChip(
                                selected = reglages.vue == valeur,
                                onClick = { vm.setVue(valeur) },
                                label = { Text(libelle) },
                            )
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Thème", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((valeur, libelle) in themes) {
                            FilterChip(
                                selected = reglages.theme == valeur,
                                onClick = { vm.setTheme(valeur) },
                                label = { Text(libelle) },
                            )
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Taille du texte du lecteur", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "S'ajoute à la taille de police choisie dans les réglages du téléphone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { vm.setTailleTexte(TailleTexte.moins(reglages.tailleTexte)) },
                            enabled = TailleTexte.peutDiminuer(reglages.tailleTexte),
                            modifier = Modifier
                                .defaultMinSize(minWidth = CIBLE_TACTILE_MIN, minHeight = CIBLE_TACTILE_MIN)
                                .semantics { contentDescription = "Réduire le texte du lecteur" },
                        ) { Text("A-") }
                        Text(
                            "${reglages.tailleTexte} %",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics {
                                contentDescription = "Texte du lecteur à ${reglages.tailleTexte} pour cent"
                            },
                        )
                        OutlinedButton(
                            onClick = { vm.setTailleTexte(TailleTexte.plus(reglages.tailleTexte)) },
                            enabled = TailleTexte.peutAugmenter(reglages.tailleTexte),
                            modifier = Modifier
                                .defaultMinSize(minWidth = CIBLE_TACTILE_MIN, minHeight = CIBLE_TACTILE_MIN)
                                .semantics { contentDescription = "Agrandir le texte du lecteur" },
                        ) { Text("A+") }
                        TextButton(
                            onClick = { vm.setTailleTexte(TailleTexte.DEFAUT) },
                            enabled = reglages.tailleTexte != TailleTexte.DEFAUT,
                        ) { Text("Rétablir") }
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Titre("Archive locale") }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Articles conservés sur le téléphone : le texte complet reste lisible hors connexion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (valeur in archives) {
                            FilterChip(
                                selected = reglages.archiveMax == valeur,
                                onClick = {
                                    portee.launch {
                                        // Sous le nombre d'articles actuel, la limite purge : on prévient.
                                        val apercu = runCatching { vm.apercuArchive(valeur) }.getOrNull()
                                        if (apercu != null && apercu.exigeConfirmation) {
                                            reductionEnAttente = apercu
                                        } else {
                                            vm.setArchiveMax(valeur)
                                        }
                                    }
                                },
                                label = { Text("${valeur / 1000}k") },
                            )
                        }
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Titre("Intelligence artificielle") }

            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "Les résumés et la synthèse passent par Ollama, sur le réseau de la maison. " +
                            "Aucun texte d'article n'est envoyé à un service extérieur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlSaisie,
                        onValueChange = { urlSaisie = it },
                        label = { Text("Adresse du serveur Ollama") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Modèle utilisé : ${reglages.ollamaModele}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = {
                            // L'adresse affichée devient celle qui sera enregistrée et testée.
                            urlSaisie = fr.fh.rss.data.net.Ollama.normaliser(urlSaisie)
                            vm.enregistrerEtTesterOllama(urlSaisie)
                        }) {
                            Text("Enregistrer et tester")
                        }
                    }
                    if (!etatOllama.isNullOrBlank()) {
                        Text(
                            etatOllama,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (fr.fh.rss.data.net.Ollama.estEtatErreur(etatOllama)) {
                                MaterialTheme.colorScheme.error
                            } else {
                                VertIa
                            },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (modelesOllama.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            for (modele in modelesOllama) {
                                FilterChip(
                                    selected = reglages.ollamaModele == modele,
                                    onClick = { vm.setOllamaModele(modele) },
                                    label = { Text(modele) },
                                )
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Titre("Sources et sauvegarde") }

            item {
                Text(
                    "$nombreSources source(s) suivie(s).",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = {
                        selecteurOpml.launch(arrayOf("text/xml", "text/x-opml", "application/xml", "*/*"))
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                        Text("  Importer OPML")
                    }
                    OutlinedButton(onClick = {
                        vm.exporterOpml { xml -> partagerOpml(contexte, xml) }
                    }) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Text("  Exporter OPML")
                    }
                }
            }

            item {
                // Les réglages n'affichent aucune liste : ici, c'est bien tout le parc
                // (une Snackbar permet d'annuler).
                TextButton(
                    onClick = { vm.marquerToutLu(Filtre.Tous) },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Text("Tout marquer comme lu (tous les articles)")
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { Titre("Mise à jour") }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Version installée : ${BuildConfig.VERSION_NAME}")
                        Text(
                            "Les mises à jour arrivent par GitHub Releases.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { UpdateManager.checkNow(contexte) }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Text("  Vérifier")
                    }
                }
            }

            item {
                Text(
                    "RSS by Fabrice Heuvrard · données stockées sur le téléphone, aucune publicité.\n" +
                        "Aucune télémétrie n'est envoyée.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    reductionEnAttente?.let { apercu ->
        DialogueReductionArchive(
            apercu = apercu,
            onConfirmer = {
                reductionEnAttente = null
                vm.setArchiveMax(apercu.limite)
            },
            onFermer = { reductionEnAttente = null },
        )
    }
}

@Composable
private fun Titre(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun LigneBascule(
    titre: String,
    detail: String,
    actif: Boolean,
    modifiable: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val attenue = if (modifiable) 1f else 0.5f
            Text(
                titre,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.alpha(attenue),
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = actif, onCheckedChange = onChange, enabled = modifiable)
    }
}

private val intervalles = listOf(
    15 to "15 min",
    30 to "30 min",
    60 to "1 h",
    180 to "3 h",
    360 to "6 h",
    720 to "12 h",
    1440 to "24 h",
)

private val vues = listOf(
    "liste" to "Liste",
    "cartes" to "Cartes",
    "magazine" to "Magazine",
)

private val themes = listOf(
    "systeme" to "Système",
    "clair" to "Clair",
    "sombre" to "Sombre",
)

private val archives = listOf(5000, 20000, 50000, 100000)
