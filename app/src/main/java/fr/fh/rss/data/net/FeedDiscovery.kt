package fr.fh.rss.data.net

import org.jsoup.Jsoup

/** Détection du flux d'un site à partir de son HTML, puis des adresses habituelles. */
object FeedDiscovery {

    private val typesFlux = setOf(
        "application/rss+xml",
        "application/atom+xml",
        "application/feed+json",
        "application/json",
        "application/rdf+xml",
        "text/xml",
        "application/xml",
    )

    /** Liens `<link rel="alternate">` d'une page HTML, dans l'ordre du document. */
    fun fromHtml(html: String, baseUrl: String): List<String> {
        val doc = runCatching { Jsoup.parse(html, baseUrl) }.getOrNull() ?: return emptyList()
        val liens = mutableListOf<String>()
        for (link in doc.select("link[href]")) {
            val rel = link.attr("rel").lowercase()
            val type = link.attr("type").lowercase().substringBefore(';').trim()
            if (!rel.contains("alternate")) continue
            if (type.isNotEmpty() && type !in typesFlux) continue
            val href = link.attr("abs:href").ifBlank { link.attr("href") }
            if (href.startsWith("http") && href !in liens) liens += href
        }
        return liens
    }

    /** Adresses à essayer quand aucune balise n'est trouvée. */
    fun candidates(siteUrl: String): List<String> {
        val base = siteUrl.trim().trimEnd('/')
        val origine = runCatching {
            val uri = java.net.URI(base)
            "${uri.scheme}://${uri.authority}"
        }.getOrNull() ?: return emptyList()
        val chemins = listOf("/feed", "/feed/", "/rss", "/rss.xml", "/atom.xml", "/index.xml", "/feed.xml")
        return chemins.map { origine + it }
    }

    /** Vrai si le corps ressemble à un flux (et non à une page HTML). */
    fun looksLikeFeed(body: String): Boolean {
        val debut = body.trimStart().take(400).lowercase()
        if (debut.startsWith("{")) return debut.contains("\"items\"") || debut.contains("\"version\"")
        return debut.contains("<rss") || debut.contains("<feed") || debut.contains("<rdf")
    }

    fun normalizeUrl(saisie: String): String {
        val net = saisie.trim()
        return when {
            net.isEmpty() -> ""
            net.startsWith("http://") || net.startsWith("https://") -> net
            net.startsWith("feed://") -> "https://" + net.removePrefix("feed://")
            else -> "https://$net"
        }
    }
}
