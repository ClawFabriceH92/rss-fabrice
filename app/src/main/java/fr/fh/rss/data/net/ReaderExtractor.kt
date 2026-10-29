package fr.fh.rss.data.net

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Extraction du texte principal d'une page web (mode lecture hors connexion).
 * Heuristique volontairement simple : on note les conteneurs par densité de
 * texte, on garde le meilleur, et on supprime la navigation résiduelle.
 */
object ReaderExtractor {

    data class Extrait(val html: String, val texte: String)

    private val conteneurs = listOf("article", "main", "div", "section", "td")

    fun extract(pageHtml: String, baseUrl: String): Extrait? {
        val doc = runCatching { Jsoup.parse(pageHtml, baseUrl) }.getOrNull() ?: return null

        doc.select("script, style, noscript, iframe, form, nav, footer, aside, header").remove()
        doc.select("[class~=(?i)(nav|menu|footer|header|sidebar|comment|share|promo|advert|cookie)]").remove()

        var meilleur: Element? = null
        var meilleurScore = 0.0
        for (sel in conteneurs) {
            for (el in doc.select(sel)) {
                val texte = el.text().trim()
                if (texte.length < 200) continue
                val paragraphes = el.select("p").size
                val liens = el.select("a").sumOf { it.text().length }
                val densite = 1.0 - liens.toDouble() / texte.length.coerceAtLeast(1)
                val score = texte.length * (0.5 + paragraphes * 0.15) * densite
                if (score > meilleurScore) {
                    meilleurScore = score
                    meilleur = el
                }
            }
        }

        val racine = meilleur ?: doc.body() ?: return null
        racine.select("a[href]").forEach { a ->
            val absolu = a.absUrl("href")
            if (absolu.isNotBlank()) a.attr("href", absolu)
        }
        racine.select("img[src]").forEach { img ->
            val absolu = img.absUrl("src")
            if (absolu.isNotBlank()) img.attr("src", absolu)
        }

        val texte = racine.text().trim()
        if (texte.length < 80) return null
        return Extrait(racine.html(), texte)
    }
}
