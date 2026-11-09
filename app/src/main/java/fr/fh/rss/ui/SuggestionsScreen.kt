package fr.fh.rss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.fh.rss.data.net.CatalogueSources
import fr.fh.rss.data.net.SuggestionsSources

/**
 * Onglet « Suggestions ».
 *
 * Deux étages : le catalogue de sources vérifiées (parcourable par thème, chaque
 * adresse a été interrogée), puis le modèle local. Celui-ci peut choisir dans le
 * catalogue, auquel cas il ne renvoie que des numéros et ne peut rien inventer ;
 * ou chercher ailleurs, et chaque adresse qu'il cite est alors interrogée avant
 * d'être ajoutable, les écartées restant visibles avec leur raison.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EcranSuggestions(
    /** Sources du catalogue pour le thème courant, déjà suivies exclues. */
    catalogue: List<CatalogueSources.Entree>,
    themes: List<String>,
    theme: String,
    onTheme: (String) -> Unit,
    motsCles: String,
    onMotsCles: (String) -> Unit,
    propositions: List<SuggestionsSources.Proposition>,
    enCours: Boolean,
    erreur: String?,
    modele: String,
    onAjouterEntree: (CatalogueSources.Entree) -> Unit,
    onChoisirDansCatalogue: () -> Unit,
    onChercherAilleurs: () -> Unit,
    onAjouter: (SuggestionsSources.Proposition) -> Unit,
    onAjouterTout: (List<SuggestionsSources.Proposition>) -> Unit,
    onEffacer: () -> Unit,
) {
    val retenues = propositions.filter { it.retenue }
    val ecartees = propositions.filterNot { it.retenue }

    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = "catalogue-entete") {
            Column(Modifier.padding(16.dp)) {
                Text("Catalogue de sources vérifiées", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Ces adresses ont toutes été interrogées : elles répondent un flux qui " +
                        "contient des articles. Choisis un thème, ou laisse le modèle choisir " +
                        "à ta place selon les sources que tu suis déjà.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = motsCles,
                    onValueChange = onMotsCles,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    singleLine = true,
                    label = { Text("Mots-clés (facultatif)") },
                    placeholder = { Text("IFRS, TVA, facture électronique, Kotlin…") },
                    supportingText = {
                        Text(
                            if (motsCles.isBlank()) {
                                "Filtre la liste, et oriente le choix du modèle."
                            } else {
                                "${catalogue.size} source(s) correspondent, dans tous les thèmes si besoin."
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (nom in themes) {
                        FilterChip(
                            selected = nom == theme,
                            onClick = { onTheme(nom) },
                            label = { Text(nom) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onChoisirDansCatalogue, enabled = !enCours) {
                        Text("Choisir avec le modèle")
                    }
                    OutlinedButton(onClick = onChercherAilleurs, enabled = !enCours) {
                        Text("Chercher ailleurs")
                    }
                }
                Text(
                    "« Choisir avec le modèle » lui fait sélectionner des sources du catalogue. " +
                        "« Chercher ailleurs » lui fait proposer d'autres sites, dont chaque " +
                        "adresse est vérifiée avant d'être ajoutable.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (enCours) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text(
                            "Interrogation du modèle ($modele) et vérification des adresses…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (erreur != null) {
                    Text(
                        erreur,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        // -------------------------------------------------------- catalogue
        items(catalogue, key = { "cat-" + it.url }) { entree ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    entree.nom,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entree.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { onAjouterEntree(entree) }) { Text("Ajouter cette source") }
                HorizontalDivider(Modifier.padding(top = 2.dp))
            }
        }

        if (catalogue.isEmpty()) {
            item(key = "catalogue-vide") {
                Text(
                    "Toutes les sources de ce thème sont déjà suivies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        // ---------------------------------------- propositions du modèle
        if (propositions.isNotEmpty()) {
            item(key = "titre-modele") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    HorizontalDivider(Modifier.padding(bottom = 10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Propositions du modèle",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (retenues.isNotEmpty()) {
                            OutlinedButton(onClick = { onAjouterTout(retenues) }, enabled = !enCours) {
                                Text("Tout ajouter (${retenues.size})")
                            }
                        }
                        TextButton(onClick = onEffacer, enabled = !enCours) { Text("Effacer") }
                    }
                    Text(
                        SuggestionsSources.resume(propositions),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(retenues, key = { "ok-" + it.url }) { proposition ->
                LigneProposition(proposition, onAjouter)
            }
            if (ecartees.isNotEmpty()) {
                item(key = "titre-ecartees") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Écartées après vérification (${ecartees.size})",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            "Ces adresses ne répondent pas un flux : le modèle les a inventées " +
                                "ou le site a fermé son RSS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(ecartees, key = { "ko-" + it.url }) { proposition ->
                    LigneProposition(proposition, onAjouter)
                }
            }
        }
    }
}

@Composable
private fun LigneProposition(
    proposition: SuggestionsSources.Proposition,
    onAjouter: (SuggestionsSources.Proposition) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (proposition.retenue) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (proposition.retenue) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                proposition.nom,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (proposition.articles > 0) {
                Text(
                    "${proposition.articles} article(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            proposition.url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (proposition.detail.isNotBlank()) {
            Text(
                proposition.detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (proposition.retenue) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
                maxLines = 2,
            )
        }
        if (proposition.retenue) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onAjouter(proposition) }) { Text("Ajouter cette source") }
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}
