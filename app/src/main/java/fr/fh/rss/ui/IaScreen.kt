package fr.fh.rss.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.fh.rss.data.Reglages
import fr.fh.rss.data.Regroupement
import fr.fh.rss.data.db.ArticleEntity

/**
 * Onglet IA : ce qui demande un modèle de langage d'un côté, ce qui se calcule
 * sans lui de l'autre.
 *
 * Les regroupements et les thèmes sont déterministes (aucun appel réseau) : ils
 * s'affichent même quand le PC est éteint. Seule la synthèse et les résumés
 * passent par Ollama.
 */
@Composable
fun EcranIa(
    articles: List<ArticleEntity>,
    titres: Map<Long, String>,
    reglages: Reglages,
    etatOllama: String?,
    synthese: String?,
    syntheseEnCours: Boolean,
    resumes: Map<Long, String>,
    resumeEnCours: Long?,
    onOuvrir: (ArticleEntity) -> Unit,
    onResumer: (ArticleEntity) -> Unit,
    onSynthese: () -> Unit,
    onEffacerSynthese: () -> Unit,
    onReglagesIa: () -> Unit,
) {
    val groupes = remember(articles, titres) {
        Regroupement.regrouper(articles.take(400), titres)
    }
    val doublons = remember(groupes) { groupes.filter { it.doublon } }
    val themes = remember(groupes) { Regroupement.parTheme(groupes) }
    val nonLus = remember(articles) { articles.count { !it.isRead } }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
    ) {
        item(key = "entete") {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Intelligence locale",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Les résumés passent par ${reglages.ollamaModele} sur ${reglages.ollamaUrl}. " +
                        "Rien ne sort de ton réseau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!etatOllama.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        etatOllama,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (etatOllama.startsWith("Injoignable")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            VertIa
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onReglagesIa) { Text("Réglages IA") }
                    Spacer(Modifier.height(0.dp))
                }
            }
        }

        item(key = "synthese") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Synthèse des non-lus", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (nonLus == 0) {
                            "Aucun article non lu pour l'instant."
                        } else {
                            "$nonLus article(s) non lu(s). Le modèle dégage les sujets qui reviennent."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = onSynthese,
                            enabled = !syntheseEnCours && nonLus > 0,
                        ) {
                            if (syntheseEnCours) {
                                CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                                Text("  Analyse…")
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Text("  Synthétiser")
                            }
                        }
                        if (!synthese.isNullOrBlank() && !syntheseEnCours) {
                            IconButton(onClick = onEffacerSynthese) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer la synthèse")
                            }
                        }
                    }
                    if (!synthese.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(synthese, style = MaterialTheme.typography.bodyMedium)
                    } else if (syntheseEnCours) {
                        Spacer(Modifier.height(12.dp))
                        BlocAttente(
                            phrases = PHRASES_SYNTHESE,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        item(key = "titreDoublons") {
            Column(Modifier.padding(16.dp)) {
                HorizontalDivider(Modifier.padding(bottom = 12.dp))
                Text("Dépêches en double", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (doublons.isEmpty()) {
                        "Aucune dépêche publiée par plusieurs sources dans les articles affichés."
                    } else {
                        "${doublons.size} dépêche(s) publiée(s) par plusieurs sources. " +
                            "Le titre ne s'affiche qu'une fois par groupe."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(doublons.take(40), key = { "dbl-${it.article.id}" }) { groupe ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOuvrir(groupe.article) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    groupe.article.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${groupe.sources.joinToString(" · ")}  ·  ${groupe.nombreSources} sources",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        item(key = "titreThemes") {
            Column(Modifier.padding(16.dp)) {
                HorizontalDivider(Modifier.padding(bottom = 12.dp))
                Text("Thèmes", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Classement par mots-clés, sans modèle : il reste le même hors connexion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        for ((theme, liste) in themes) {
            item(key = "theme-$theme") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        theme,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${liste.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(liste.take(4), key = { "th-$theme-${it.article.id}" }) { groupe ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOuvrir(groupe.article) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            groupe.article.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            titres[groupe.article.feedId].orEmpty() + "  ·  " +
                                dateCourte(groupe.article.publishedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (resumeEnCours == groupe.article.id) {
                        CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
                    } else if (resumes.containsKey(groupe.article.id)) {
                        Text(
                            "résumé",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        IconButton(onClick = { onResumer(groupe.article) }) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Résumer",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            if (liste.size > 4) {
                item(key = "suite-$theme") {
                    Text(
                        "+ ${liste.size - 4} autre(s) article(s) dans ce thème",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}
