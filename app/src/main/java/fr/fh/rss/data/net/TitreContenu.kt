package fr.fh.rss.data.net

import org.jsoup.Jsoup

/**
 * Nettoyage du contenu d'un article avant affichage dans le lecteur.
 *
 * Le lecteur ajoute lui-même le titre de l'article en tête de page. Or beaucoup
 * de flux commencent leur contenu par ce même titre (h1 ou h2), ce qui donnait
 * deux fois le titre à l'écran. On retire donc le premier titre du contenu
 * lorsque son texte correspond à celui de l'article ; un premier titre
 * different est conservé.
 */
object TitreContenu {

    /** Longueur minimale d'un titre pour accepter une comparaison approchée. */
    private const val LONGUEUR_MIN_APPROCHE = 12

    /**
     * Retire le premier titre du contenu s'il répète [titre].
     *
     * @return le HTML du contenu, éventuellement amputé de son titre d'ouverture.
     */
    fun retirerTitreDuplique(contenuHtml: String?, titre: String?): String {
        if (contenuHtml.isNullOrBlank() || titre.isNullOrBlank()) return contenuHtml.orEmpty()

        val document = Jsoup.parseBodyFragment(contenuHtml)
        val corps = document.body()

        // Premier titre du contenu, dans l'ordre du document.
        val premierTitre = corps.selectFirst("h1, h2, h3") ?: return contenuHtml
        if (!precedeeDeRien(corps, premierTitre)) return contenuHtml

        val texteTitre = normaliser(premierTitre.text())
        if (texteTitre.isEmpty()) return contenuHtml

        if (!correspond(texteTitre, normaliser(titre))) return contenuHtml

        premierTitre.remove()
        return corps.html()
    }

    /** Le titre doit ouvrir le contenu : rien de consistant avant lui. */
    private fun precedeeDeRien(corps: org.jsoup.nodes.Element, titre: org.jsoup.nodes.Element): Boolean {
        var texteAvant = ""
        for (element in corps.getAllElements()) {
            if (element === titre) return texteAvant.length < 40
            if (element === corps) continue
            // On ne compte que le texte propre aux éléments qui precedent le titre.
            if (titre.parents().contains(element)) continue
            texteAvant += element.ownText()
            if (texteAvant.length >= 40) return false
        }
        return true
    }

    private fun correspond(texteContenu: String, texteArticle: String): Boolean {
        if (texteContenu.isEmpty() || texteArticle.isEmpty()) return false
        if (texteContenu == texteArticle) return true

        val contenuCourt = texteContenu.length <= texteArticle.length
        val court = if (contenuCourt) texteContenu else texteArticle
        val long = if (contenuCourt) texteArticle else texteContenu
        return court.length >= LONGUEUR_MIN_APPROCHE &&
            court.length * 100 >= long.length * 85 &&
            long.contains(court)
    }

    /** Minuscules, sans accents, ponctuation ni espaces multiples. */
    internal fun normaliser(texte: String): String {
        val sansAccents = java.text.Normalizer
            .normalize(texte.replace('\u00a0', ' '), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return sansAccents
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
