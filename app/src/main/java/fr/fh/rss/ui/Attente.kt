package fr.fh.rss.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Animation d'attente pour les traitements longs (résumé, synthèse).
 *
 * Trois points qui respirent, une phrase qui tourne et un compteur de secondes :
 * l'utilisateur voit que le travail avance, et les lignes fantômes annoncent la
 * forme du texte qui va s'afficher.
 */
@Composable
fun BlocAttente(
    phrases: List<String>,
    modifier: Modifier = Modifier,
    secondesSansNouvelle: Int = 20,
) {
    var secondes by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        secondes = 0
        while (true) {
            delay(1000)
            secondes++
        }
    }

    val transition = rememberInfiniteTransition(label = "attente")
    val premiere by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 0, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "point1",
    )
    val deuxieme by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 220, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "point2",
    )
    val troisieme by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, delayMillis = 440, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "point3",
    )

    val phrase = phrases[(secondes / 4) % phrases.size]
    val teinte = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row {
                for (alpha in listOf(premiere, deuxieme, troisieme)) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .alpha(alpha * 0.9f + 0.1f)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(5.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "$phrase · ${secondes} s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        for (fraction in listOf(1f, 0.92f, 0.6f)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(teinte.copy(alpha = 0.10f + 0.10f * premiere)),
            )
            Spacer(Modifier.height(6.dp))
        }
        if (secondes >= secondesSansNouvelle) {
            Text(
                "Le modèle local travaille, une minute peut être nécessaire sur un long article.",
                style = MaterialTheme.typography.labelSmall,
                color = teinte,
            )
        }
    }
}

/** Phrases d'attente du résumé d'article. */
val PHRASES_RESUME = listOf(
    "Lecture de l'article",
    "Repérage des points clés",
    "Rédaction du résumé",
)

/** Phrases d'attente de la synthèse des non-lus. */
val PHRASES_SYNTHESE = listOf(
    "Lecture des titres",
    "Recherche des sujets récurrents",
    "Rédaction de la synthèse",
)
