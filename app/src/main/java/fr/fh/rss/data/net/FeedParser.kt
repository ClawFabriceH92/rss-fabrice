package fr.fh.rss.data.net

import fr.fh.rss.data.model.ParsedFeed
import fr.fh.rss.data.model.ParsedItem
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.Locale

/**
 * Analyse des flux RSS 2.0 / RSS 1.0 (RDF), Atom et JSON Feed.
 *
 * Le HTML et le XML passent par jsoup (analyseur tolérant) plutôt que par
 * XmlPullParser : un flux mal formé doit produire un maximum d'articles, pas
 * une exception, et le même code doit tourner dans les tests JVM.
 */
object FeedParser {

    fun parse(feedUrl: String, body: String): ParsedFeed {
        val debut = body.trimStart()
        if (debut.isEmpty()) return ParsedFeed(hostOf(feedUrl), null, "rss", emptyList())
        return if (debut.startsWith("{")) parseJson(feedUrl, body) else parseXml(feedUrl, body)
    }

    // ---------------------------------------------------------------- XML

    private fun parseXml(feedUrl: String, body: String): ParsedFeed {
        val doc = Jsoup.parse(body, "", Parser.xmlParser())
        val racine = doc.children().firstOrNull()
            ?: return ParsedFeed(hostOf(feedUrl), null, "rss", emptyList())
        return when (localName(racine.tagName())) {
            "feed" -> parseAtom(feedUrl, doc, racine)
            else -> parseRss(feedUrl, doc)
        }
    }

    private fun parseRss(feedUrl: String, doc: Document): ParsedFeed {
        val channel = doc.selectFirst("channel") ?: doc.children().firstOrNull()
        val titre = channel?.childText("title").orEmpty().ifBlank { hostOf(feedUrl) }
        val site = channel?.childText("link")
        val items = doc.select("item").mapNotNull { itemFrom(it) }
        return ParsedFeed(titre, site?.takeIf { it.startsWith("http") }, "rss", items)
    }

    private fun parseAtom(feedUrl: String, doc: Document, feed: Element): ParsedFeed {
        val titre = feed.childText("title").orEmpty().ifBlank { hostOf(feedUrl) }
        val site = feed.childrenNamed("link")
            .firstOrNull { it.attr("href").isNotBlank() && it.attr("rel") != "self" }
            ?.attr("href")
        val entries = doc.select("entry").mapNotNull { entryFrom(it) }
        return ParsedFeed(titre, site, "atom", entries)
    }

    private fun itemFrom(el: Element): ParsedItem? {
        val titreBrut = el.childText("title")
        val lien = el.childText("link")
            ?: el.attr("rdf:about").takeIf { it.isNotBlank() }
            ?: el.childrenNamed("link").firstOrNull()?.attr("href")?.takeIf { it.isNotBlank() }
        val guid = el.childText("guid")
            ?: lien
            ?: el.childText("pubdate")?.let { "$titreBrut|$it" }
            ?: titreBrut
            ?: return null

        val contenu = el.childrenNamed("encoded").firstOrNull()?.wholeText()
            ?: el.childrenNamed("content").firstOrNull()?.wholeText()
        val resume = el.childrenNamed("description").firstOrNull()?.wholeText()

        return ParsedItem(
            guid = guid.trim(),
            url = lien?.trim()?.takeIf { it.startsWith("http") },
            title = titreBrut?.let { texteSimple(it) }?.ifBlank { "(sans titre)" } ?: "(sans titre)",
            author = el.childText("creator", "author", "name")?.let { texteSimple(it) },
            publishedAt = Dates.parse(
                el.childText("pubdate", "published", "updated", "date"),
            ) ?: System.currentTimeMillis(),
            summaryHtml = resume?.takeIf { it.isNotBlank() },
            contentHtml = (contenu ?: resume)?.takeIf { it.isNotBlank() },
            imageUrl = imageFrom(el, contenu ?: resume),
        )
    }

    private fun entryFrom(el: Element): ParsedItem? {
        val titre = el.childText("title")
        val lien = el.childrenNamed("link")
            .firstOrNull { it.attr("href").isNotBlank() && it.attr("rel") != "self" }
            ?.attr("href")
            ?: el.childText("id")?.takeIf { it.startsWith("http") }
        val guid = el.childText("id") ?: lien ?: titre ?: return null
        val contenu = el.childrenNamed("content").firstOrNull()?.wholeText()
        val resume = el.childrenNamed("summary").firstOrNull()?.wholeText()

        return ParsedItem(
            guid = guid.trim(),
            url = lien?.trim(),
            title = titre?.let { texteSimple(it) }?.ifBlank { "(sans titre)" } ?: "(sans titre)",
            author = el.childrenNamed("author").firstOrNull()
                ?.childText("name")
                ?.let { texteSimple(it) },
            publishedAt = Dates.parse(el.childText("published", "updated", "issued"))
                ?: System.currentTimeMillis(),
            summaryHtml = resume?.takeIf { it.isNotBlank() },
            contentHtml = (contenu ?: resume)?.takeIf { it.isNotBlank() },
            imageUrl = imageFrom(el, contenu ?: resume),
        )
    }

    // --------------------------------------------------------------- JSON

    private fun parseJson(feedUrl: String, body: String): ParsedFeed {
        val racine = runCatching { JSONObject(body) }.getOrNull()
            ?: return ParsedFeed(hostOf(feedUrl), null, "json", emptyList())
        val titre = racine.optString("title", "").ifBlank { hostOf(feedUrl) }
        val site = racine.optString("home_page_url", "").takeIf { it.isNotBlank() }
        val items = racine.optJSONArray("items") ?: JSONArray()

        val articles = (0 until items.length()).mapNotNull { index ->
            val o = items.optJSONObject(index) ?: return@mapNotNull null
            val lien = o.optString("url", "")
            val guid = o.optString("id", "").ifBlank { lien }
            if (guid.isBlank()) return@mapNotNull null

            val html = o.optString("content_html", "")
            val texte = o.optString("content_text", "")
            val contenu = html.ifBlank { texte }
            val auteur = o.optJSONObject("author")?.optString("name", "").orEmpty()
                .ifBlank { o.optString("author", "") }

            ParsedItem(
                guid = guid.trim(),
                url = lien.takeIf { it.isNotBlank() },
                title = o.optString("title", "").let { texteSimple(it) }
                    .ifBlank { "(sans titre)" },
                author = auteur.takeIf { it.isNotBlank() },
                publishedAt = Dates.parse(o.optString("date_published", ""))
                    ?: Dates.parse(o.optString("date_modified", ""))
                    ?: System.currentTimeMillis(),
                summaryHtml = o.optString("summary", "").takeIf { it.isNotBlank() },
                contentHtml = contenu.takeIf { it.isNotBlank() },
                imageUrl = o.optString("image", "").takeIf { it.isNotBlank() }
                    ?: o.optString("banner_image", "").takeIf { it.isNotBlank() },
            )
        }
        return ParsedFeed(titre, site, "json", articles)
    }

    // ------------------------------------------------------------- outils

    private fun Element.childrenNamed(nom: String): List<Element> =
        children().filter { localName(it.tagName()) == nom }

    /** Texte d'un enfant, en cherchant plusieurs noms possibles. */
    fun Element.childText(vararg noms: String): String? {
        for (nom in noms) {
            val el = childrenNamed(nom).firstOrNull() ?: continue
            val valeur = el.text().trim()
            if (valeur.isNotEmpty()) return valeur
        }
        return null
    }

    private fun imageFrom(el: Element, contenu: String?): String? {
        el.childrenNamed("thumbnail").firstOrNull()?.let {
            listOf("url", "href").forEach { attr ->
                val v = it.attr(attr)
                if (v.startsWith("http")) return v
            }
        }
        el.childrenNamed("content").forEach {
            val type = it.attr("type")
            val url = it.attr("url")
            if (url.startsWith("http") && (type.startsWith("image") || type.isEmpty())) return url
        }
        el.childrenNamed("enclosure").forEach {
            val url = it.attr("url")
            val type = it.attr("type")
            if (url.startsWith("http") && (type.startsWith("image") || type.isEmpty())) return url
        }
        if (!contenu.isNullOrBlank()) {
            val img = runCatching {
                Jsoup.parse(contenu).selectFirst("img[src]")?.attr("src")
            }.getOrNull()
            if (!img.isNullOrBlank() && img.startsWith("http")) return img
        }
        return null
    }

    fun texteSimple(html: String?): String =
        if (html.isNullOrBlank()) "" else Jsoup.parse(html).text().trim()

    private fun localName(tag: String): String =
        tag.substringAfter(':').lowercase(Locale.ROOT)

    fun hostOf(url: String): String = runCatching {
        java.net.URI(url).host?.removePrefix("www.")
    }.getOrNull() ?: url
}
