package fr.fh.rss.ui

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView

/** Lecteur intégré : HTML nettoyé, JavaScript désactivé. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranLecteur(
    vm: MainViewModel,
    articleId: Long,
    onRetour: () -> Unit,
) {
    val article by vm.observerArticle(articleId).collectAsState(initial = null)
    val sources by vm.sources.collectAsState()
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

    val source = sources.firstOrNull { it.id == courant.feedId }?.title.orEmpty()
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
                    IconButton(onClick = { vm.basculerFavori(courant.id, !courant.isStarred) }) {
                        Icon(
                            if (courant.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Lire plus tard",
                        )
                    }
                    IconButton(
                        onClick = {
                            courant.url?.let { lien ->
                                runCatching {
                                    contexte.startActivity(
                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(lien))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
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
                },
            )
        },
    ) { marges ->
        val contenu = courant.contentHtml
            ?: courant.summaryHtml
            ?: courant.textContent?.split("\n\n")?.joinToString("") { "<p>${TextUtils.htmlEncode(it)}</p>" }
            ?: "<p>Contenu indisponible. Ouvrez l'article dans le navigateur.</p>"

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
            append("</style></head><body>")
            append("<h1>").append(TextUtils.htmlEncode(courant.title)).append("</h1>")
            append("<p class=\"meta\">")
            append(TextUtils.htmlEncode(source))
            append(" · ").append(dateLongue(courant.publishedAt))
            if (!courant.author.isNullOrBlank()) {
                append(" · ").append(TextUtils.htmlEncode(courant.author))
            }
            append("</p>")
            append(contenu)
            append("</body></html>")
        }

        AndroidView(
            modifier = Modifier.padding(marges).fillMaxSize(),
            factory = { ctx -> creerWebView(ctx) },
            update = { vue -> vue.loadDataWithBaseURL(courant.url, html, "text/html", "utf-8", null) },
        )
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
