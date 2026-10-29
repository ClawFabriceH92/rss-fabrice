package fr.fh.rss.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.Locale

/** Import et export OPML. Les dossiers sont portés par les `outline` parents. */
object Opml {

    data class Entree(
        val titre: String,
        val xmlUrl: String,
        val htmlUrl: String? = null,
        val dossier: String? = null,
    )

    /** Toutes les sources d'un OPML, avec leur dossier éventuel. */
    fun parse(xml: String): List<Entree> {
        val doc = runCatching { Jsoup.parse(xml, "", Parser.xmlParser()) }.getOrNull()
            ?: return emptyList()
        val body = doc.selectFirst("body") ?: doc.children().firstOrNull() ?: return emptyList()
        val entrees = mutableListOf<Entree>()
        parcourir(body, null, entrees)
        return entrees
    }

    private fun parcourir(parent: Element, dossier: String?, sortie: MutableList<Entree>) {
        for (outline in parent.children().filter { it.tagName().lowercase(Locale.ROOT) == "outline" }) {
            val xmlUrl = attribut(outline, "xmlurl")
            val htmlUrl = attribut(outline, "htmlurl")
            val titre = attribut(outline, "title") ?: attribut(outline, "text")
            if (!xmlUrl.isNullOrBlank()) {
                sortie += Entree(
                    titre = titre?.ifBlank { null } ?: xmlUrl,
                    xmlUrl = xmlUrl.trim(),
                    htmlUrl = htmlUrl?.takeIf { it.isNotBlank() },
                    dossier = dossier,
                )
            } else {
                val nomDossier = titre?.ifBlank { null } ?: dossier
                parcourir(outline, nomDossier, sortie)
            }
        }
    }

    private fun attribut(el: Element, nom: String): String? =
        el.attributes().firstOrNull { it.key.lowercase(Locale.ROOT) == nom }?.value

    /**
     * [dossiers] : chaque élément associe un nom de dossier (null = sans
     * dossier) à la liste (titre du flux, url du flux).
     */
    fun export(dossiers: List<Pair<String?, List<Pair<String, String>>>>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head>\n")
        sb.append("    <title>RSS by Fabrice Heuvrard</title>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")
        for ((dossier, flux) in dossiers) {
            if (dossier == null) {
                for ((titre, url) in flux) sb.append(outlineFlux(titre, url, "    "))
            } else {
                sb.append("    <outline text=\"${echapper(dossier)}\" title=\"${echapper(dossier)}\">\n")
                for ((titre, url) in flux) sb.append(outlineFlux(titre, url, "      "))
                sb.append("    </outline>\n")
            }
        }
        sb.append("  </body>\n")
        sb.append("</opml>\n")
        return sb.toString()
    }

    private fun outlineFlux(titre: String, url: String, indent: String): String =
        "$indent<outline type=\"rss\" text=\"${echapper(titre)}\" title=\"${echapper(titre)}\" " +
            "xmlUrl=\"${echapper(url)}\" />\n"

    private fun echapper(valeur: String): String = valeur
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
