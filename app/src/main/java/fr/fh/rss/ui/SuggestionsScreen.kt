package fr.fh.rss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.fh.rss.data.net.SuggestionsSources

/**
 * Onglet « Suggestions » : le modèle local propose des sources, l'application
 * vérifie chaque adresse par le réseau avant de l'afficher comme ajoutable.
 *
 * Un modèle de langage invente des adresses de flux : la vérification est ce
 * qui sépare une suggestion utile d'une source morte de plus. Les adresses
 * écartées restent visibles, avec la raison, plutôt que d'être masquées.
 */
@Composable
fun EcranSuggestions(
    propositions: List<SuggestionsSources.Proposition>,
    enCours: Boolean,
    erreur: String?,
    modele: String,
    onDemander: () -> Unit,
    onAjouter: (SuggestionsSources.Proposition) -> Unit,
    onAjouterTout: (List<SuggestionsSources.Proposition>) -> Unit,
    onEffacer: () -> Unit,
) {
    val retenues = propositions.filter { it.retenue }
    val ecartees = propositions.filterNot { it.retenue }

    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = "entete") {
            Column(Modifier.padding(16.dp)) {
                Text("Sources proposées par l'IA", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Le modèle ($modele) lit les sources que tu suis déjà et propose des " +
                        "compléments. Chaque adresse est ensuite interrogée : seules celles " +
                        "qui répondent vraiment un flux sont ajoutables. Rien n'est abonné " +
                        "sans ton accord.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onDemander, enabled = !enCours) {
                        Text(if (propositions.isEmpty()) "Demander des idées" else "Redemander")
                    }
                    if (propositions.isNotEmpty() && retenues.isNotEmpty()) {
                        OutlinedButton(onClick = { onAjouterTout(retenues) }, enabled = !enCours) {
                            Text("Tout ajouter (${retenues.size})")
                        }
                    }
                    if (propositions.isNotEmpty()) {
                        TextButton(onClick = onEffacer, enabled = !enCours) { Text("Effacer") }
                    }
                }
                if (enCours) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text(
                            "Interrogation du modèle local, puis vérification des adresses…",
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
                if (propositions.isNotEmpty()) {
                    Text(
                        SuggestionsSources.resume(propositions),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (propositions.isEmpty() && !enCours && erreur == null) {
                    Text(
                        "Aucune proposition pour l'instant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        items(retenues, key = { "ok-" + it.url }) { proposition ->
            LigneProposition(proposition, onAjouter)
        }

        if (ecartees.isNotEmpty()) {
            item(key = "titre-ecartees") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    HorizontalDivider(Modifier.padding(bottom = 8.dp))
                    Text(
                        "Écartées après vérification (${ecartees.size})",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "Ces adresses ne répondent pas un flux : le modèle les a inventées ou " +
                            "le site a fermé son RSS.",
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
            if (proposition.retenue) {
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
                color = MaterialTheme.colorScheme.error,
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
