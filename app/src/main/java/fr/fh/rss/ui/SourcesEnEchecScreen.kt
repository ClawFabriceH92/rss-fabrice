package fr.fh.rss.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.fh.rss.data.EtatSource
import fr.fh.rss.data.db.FeedEntity

/**
 * Écran dédié aux sources qui ne répondent plus.
 *
 * Il a son propre écran : dans les réglages il fallait faire défiler toute la
 * page pour atteindre la liste. Rien n'est supprimé automatiquement, et une
 * source n'est signalée « à examiner » qu'après le seuil d'échecs consécutifs.
 */
@Composable
fun EcranSourcesEnEchec(
    sources: List<FeedEntity>,
    onReessayer: (Long) -> Unit,
    onReessayerTout: (List<Long>) -> Unit,
    onSupprimer: (Long) -> Unit,
) {
    val maintenant = remember(sources) { System.currentTimeMillis() }

    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = "entete") {
            Column(Modifier.padding(16.dp)) {
                if (sources.isEmpty()) {
                    Text("Aucune source en échec.", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Toutes les sources ont répondu à la dernière collecte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "${sources.size} source(s) sans réponse",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "La date du premier échec est conservée : une source n'est signalée " +
                            "« à examiner » qu'après ${EtatSource.SEUIL_PROPOSITION_JOURS} jours " +
                            "d'échec. Rien n'est supprimé sans ton accord.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { onReessayerTout(sources.map { it.id }) }) {
                            Text("Tout réessayer")
                        }
                    }
                }
            }
        }

        items(sources, key = { it.id }) { feed ->
            val due = EtatSource.propositionDue(feed.errorSince, maintenant)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (due) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        feed.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (due) {
                        Text(
                            "À examiner",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    feed.lastError ?: "erreur inconnue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
                Text(
                    EtatSource.resume(feed.errorSince, feed.errorCount, maintenant),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    feed.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onReessayer(feed.id) }) { Text("Réessayer") }
                    TextButton(onClick = { onSupprimer(feed.id) }) { Text("Supprimer la source") }
                }
                HorizontalDivider(Modifier.padding(top = 4.dp))
            }
        }
    }
}
