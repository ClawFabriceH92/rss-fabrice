package fr.fh.rss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.fh.rss.data.Nettoyage

/**
 * Onglet « Nettoyage » du paramétrage : l'application propose les sources à
 * retirer, l'utilisateur coche puis confirme.
 *
 * Trois familles de sources y arrivent : celles qui échouent depuis longtemps,
 * celles qui répondent encore mais ne publient plus depuis plus d'un an (elles
 * n'apparaissent pas dans « Flux en erreur », puisque leur flux est valide), et
 * celles qui n'ont jamais rapporté un seul article. Rien n'est coché d'office :
 * une case cochée par défaut finit toujours par être validée par erreur.
 */
@Composable
fun EcranNettoyage(
    propositions: List<Nettoyage.Proposition>,
    enCours: Boolean,
    onAnalyser: () -> Unit,
    /** La suppression passe par la confirmation de l'écran principal, qui rappelle les favoris conservés. */
    onSupprimer: (List<Long>) -> Unit,
) {
    var coches by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // Un rafraîchissement des propositions ne doit pas laisser de cases fantômes.
    val idsValides = propositions.map { it.id }.toSet()
    val selection = coches.intersect(idsValides)

    LaunchedEffect(Unit) { onAnalyser() }

    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = "entete") {
            Column(Modifier.padding(16.dp)) {
                Text("Sources à retirer", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Sont proposées : les sources qui échouent depuis plus de " +
                        "${Nettoyage.SEUIL_ECHEC_JOURS} jours, celles qui répondent mais ne publient " +
                        "plus depuis plus d'un an, et celles qui n'ont jamais rapporté d'article. " +
                        "Rien n'est supprimé sans ta confirmation, et les articles favoris sont conservés.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (enCours) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text("Analyse des sources…", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        Nettoyage.resume(propositions),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (propositions.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { coches = if (selection.size == propositions.size) emptySet() else idsValides },
                        ) {
                            Text(if (selection.size == propositions.size) "Tout décocher" else "Tout cocher")
                        }
                        Button(
                            onClick = { onSupprimer(selection.toList()) },
                            enabled = selection.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text("Supprimer (${selection.size})")
                        }
                        TextButton(onClick = onAnalyser) { Text("Réanalyser") }
                    }
                }
            }
        }

        items(propositions, key = { it.id }) { proposition ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = proposition.id in selection,
                    onCheckedChange = { actif ->
                        coches = if (actif) coches + proposition.id else coches - proposition.id
                    },
                )
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        proposition.titre,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        proposition.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                    )
                    Text(
                        proposition.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}
