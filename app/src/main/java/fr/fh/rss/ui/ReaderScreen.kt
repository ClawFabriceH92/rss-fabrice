package fr.fh.rss.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.webkit.WebView
import fr.fh.rss.data.Avertissements
import fr.fh.rss.data.TailleTexte
import fr.fh.rss.data.net.TitreContenu
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Lecteur intégré : HTML nettoyé, JavaScript désactivé. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranLecteur(
    vm: MainViewModel,
    articleId: Long,
    onRetour: () -> Unit,
    onDesabonnement: (Long) -> Unit = {},
    /** Autres sources qui ont publié le même article. */
    autresVersions: List<fr.fh.rss.data.Regroupement.AutreVersion> = emptyList(),
    onOuvrirVersion: (Long) -> Unit = {},
) {
    var menuActions by remember { mutableStateOf(false) }
    // Un seul Flow par article : sans remember, chaque recomposition relançait la
    // requête Room et relisait le corps complet.
    val fluxArticle = remember(articleId) { vm.observerArticle(articleId) }
    val article by fluxArticle.collectAsState(initial = null)
    val reglages by vm.reglages.collectAsState()
    val echellePolice = LocalDensity.current.fontScale
    val zoomTexte = TailleTexte.zoomWebView(reglages.tailleTexte, echellePolice)
    val sources by vm.sources.collectAsState()
    val resumes by vm.resumes.collectAsState()
    val resumeEnCours by vm.resumeEnCours.collectAsState()
    val contexte = LocalContext.current

    LaunchedEffect(articleId) {
        vm.marquerLu(articleId)
        vm.completerArticle(articleId)
    }

    val courant = article
    if (courant == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val sourceExiste = sources.any { it.id == courant.feedId }
    val source = if (sourceExiste || sources.isEmpty()) {
        sources.firstOrNull { it.id == courant.feedId }?.title.orEmpty()
    } else {
        // Favori conservé après la suppression de sa source.
        fr.fh.rss.data.Avertissements.SOURCE_SUPPRIMEE
    }
    val couleurTexte = MaterialTheme.colorScheme.onBackground.toArgb()
    val couleurFond = MaterialTheme.colorScheme.background.toArgb()
    val couleurAccent = MaterialTheme.colorScheme.primary.toArgb()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(source.ifBlank { "Article" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (resumeEnCours == courant.id) {
                        IconButton(onClick = { vm.annulerResume() }) {
                            Icon(Icons.Default.Close, contentDescription = "Annuler le résumé")
                        }
                    } else {
                        IconButton(onClick = { vm.resumerArticle(courant.id) }) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Résumer avec l'IA locale",
                            )
                        }
                    }
                    IconButton(onClick = { vm.basculerFavori(courant.id, !courant.isStarred) }) {
                        Icon(
                            if (courant.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = TextesAccessibilite.favori(courant.isStarred),
                        )
                    }
                    IconButton(
                        onClick = {
                            val lien = courant.url?.trim()
                            if (lien.isNullOrEmpty()) {
                                vm.signaler(Avertissements.ARTICLE_SANS_ADRESSE)
                            } else {
                                try {
                                    contexte.startActivity(
                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(lien))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    vm.signaler(Avertissements.AUCUN_NAVIGATEUR)
                                } catch (_: RuntimeException) {
                                    // Adresse inexploitable (schéma refusé, intent invalide).
                                    vm.signaler(Avertissements.AUCUN_NAVIGATEUR)
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Ouvrir dans le navigateur")
                    }
                    IconButton(
                        onClick = {
                            val texte = "${courant.title}\n${courant.url.orEmpty()}"
                            runCatching {
                                val envoi = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, texte)
                                }
                                contexte.startActivity(
                                    Intent.createChooser(envoi, "Partager l'article")
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Partager")
                    }
                    // Se désabonner depuis l'article : on lit un article dont on
                    // ne veut plus, sans devoir retrouver la source dans le tiroir.
                    Box {
                        IconButton(onClick = { menuActions = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Plus d'actions",
                            )
                        }
                        DropdownMenu(
                            expanded = menuActions,
                            onDismissRequest = { menuActions = false },
                        ) {
                            for (version in autresVersions) {
                                DropdownMenuItem(
                                    text = { Text("Version : ${version.source}") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.OpenInNew,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuActions = false
                                        onOuvrirVersion(version.articleId)
                                    },
                                )
                            }
                            if (autresVersions.isNotEmpty()) HorizontalDivider()
                            if (sourceExiste) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Se désabonner de « ${source.ifBlank { "ce flux" }} »",
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.NotificationsOff,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        menuActions = false
                                        // La confirmation (favoris conservés) est gérée par l'écran principal.
                                        onDesabonnement(courant.feedId)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { marges ->
        val contenu = courant.contentHtml
            ?: courant.summaryHtml
            ?: courant.textContent?.split("\n\n")?.joinToString("") { "<p>${TextUtils.htmlEncode(it)}</p>" }
            ?: "<p>Contenu indisponible. Ouvrez l'article dans le navigateur.</p>"

        // Beaucoup de flux commencent par le titre de l'article : le lecteur
        // l'ajoute déjà en tête, on évite de l'afficher deux fois.
        val contenuSansTitre = TitreContenu.retirerTitreDuplique(contenu, courant.title)

        val html = buildString {
            append("<!doctype html><html><head>")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<style>")
            append("body{font-family:Georgia,serif;font-size:17px;line-height:1.6;")
            append("color:#").append(hex(couleurTexte)).append(";")
            append("background:#").append(hex(couleurFond)).append(";margin:16px;}")
            append("h1{font-size:22px;line-height:1.3;margin:0 0 6px 0;}")
            append(".meta{font-family:sans-serif;font-size:13px;color:#").append(hex(couleurAccent))
            append(";margin:0 0 18px 0;}")
            append("img,video,iframe{max-width:100%;height:auto;}")
            append("a{color:#").append(hex(couleurAccent)).append(";}")
            append("pre,code{white-space:pre-wrap;word-break:break-word;font-size:14px;}")
            append("blockquote{border-left:3px solid #").append(hex(couleurAccent))
            append(";margin:12px 0;padding-left:12px;}")
            append(".resume{font-family:sans-serif;font-size:15px;line-height:1.5;")
            append("padding:12px 14px;margin:0 0 18px 0;border-radius:10px;")
            append("border:1px solid #").append(hex(couleurAccent)).append(";}")
            append(".resume b{display:block;font-size:12px;letter-spacing:.04em;")
            append("text-transform:uppercase;color:#").append(hex(couleurAccent))
            append(";margin-bottom:6px;}")
            append("</style></head><body>")
            append("<h1>").append(TextUtils.htmlEncode(courant.title)).append("</h1>")
            append("<p class=\"meta\">")
            append(TextUtils.htmlEncode(source))
            append(" · ").append(dateLongue(courant.publishedAt))
            if (!courant.author.isNullOrBlank()) {
                append(" · ").append(TextUtils.htmlEncode(courant.author))
            }
            append("</p>")
            val resumeLocal = resumes[courant.id]
            if (!resumeLocal.isNullOrBlank()) {
                append("<div class=\"resume\"><b>Résumé local</b>")
                append(TextUtils.htmlEncode(resumeLocal).replace("\n", "<br>"))
                append("</div>")
            }
            append(contenuSansTitre)
            append("</body></html>")
        }

        val cleAffichage = "${courant.id}|${html.hashCode()}"
        Box(Modifier.padding(marges).fillMaxSize()) {
            key(courant.id) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> creerWebView(ctx) },
                    // Libère le moteur de rendu en quittant le lecteur : un WebView
                    // abandonné garde sa mémoire et ses processus.
                    onRelease = { vue ->
                        vue.stopLoading()
                        vue.destroy()
                    },
                    update = { vue ->
                        // Taille du texte : réglage A- / A+ × échelle de police du système.
                        // Sans rechargement, la position de lecture est conservée.
                        if (vue.settings.textZoom != zoomTexte) vue.settings.textZoom = zoomTexte
                        // Ne recharger que si le contenu a réellement changé.
                        // Marquer l'article comme lu réémet la fiche (isRead passe
                        // à true) : recharger à ce moment-là renvoyait la lecture
                        // en haut de l'article au moindre défilement.
                        if (vue.tag != cleAffichage) {
                            vue.tag = cleAffichage
                            vue.loadDataWithBaseURL(
                                courant.url,
                                html,
                                "text/html",
                                "utf-8",
                                null,
                            )
                        }
                    },
                )
            }
            // Le texte complet n'a pas pu être téléchargé : on le dit, discrètement,
            // plutôt que de laisser croire que le résumé du flux est tout l'article.
            if (courant.texteCompletEchec) {
                Text(
                    text = fr.fh.rss.data.Avertissements.TEXTE_COMPLET_INDISPONIBLE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            // Pendant le résumé, l'attente s'affiche par-dessus la lecture :
            // l'article reste visible et lisible sous le bandeau.
            if (resumeEnCours == courant.id) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    BlocAttente(
                        phrases = PHRASES_RESUME,
                        modifier = Modifier.padding(14.dp),
                    )
                    TextButton(
                        onClick = { vm.annulerResume() },
                        modifier = Modifier.align(Alignment.End).padding(end = 8.dp),
                    ) { Text("Annuler") }
                }
            }
        }
    }
}

private fun creerWebView(contexte: Context): WebView = WebView(contexte).apply {
    settings.javaScriptEnabled = false
    settings.domStorageEnabled = false
    settings.loadsImagesAutomatically = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
}

private fun hex(argb: Int): String = String.format("%06X", 0xFFFFFF and argb)
