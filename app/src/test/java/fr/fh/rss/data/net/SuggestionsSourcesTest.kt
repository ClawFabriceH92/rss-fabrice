package fr.fh.rss.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extraction et filtrage des sources proposées par le modèle local.
 *
 * Le point sensible : un modèle cite des adresses qu'il invente, et il ne
 * respecte pas toujours le format demandé. L'extraction doit donc survivre à
 * une liste à puces comme à du JSON, et le filtrage ne jamais reproposer une
 * source déjà suivie.
 */
class SuggestionsSourcesTest {

    @Test
    fun `le tableau JSON est lu et les adresses conservees telles quelles`() {
        val reponse = """
            Voici des idées :
            [{"nom": "Dalloz actualité", "url": "https://www.dalloz-actualite.fr/rss.xml"},
             {"nom": "Kotlin", "url": "http://blog.jetbrains.com/kotlin/feed/"}]
        """.trimIndent()

        val extraites = SuggestionsSources.extraire(reponse)

        assertEquals(2, extraites.size)
        assertEquals("Dalloz actualité" to "https://www.dalloz-actualite.fr/rss.xml", extraites[0])
        // http:// n'est pas promu en https:// : l'adresse citée est celle qu'on testera.
        assertEquals("Kotlin" to "http://blog.jetbrains.com/kotlin/feed/", extraites[1])
    }

    @Test
    fun `une liste a puces est acceptee quand le modele ignore la consigne`() {
        val reponse = """
            - Le Monde Economie : https://www.lemonde.fr/economie/rss_full.xml
            - Next : https://next.ink/feed/
            * LinuxFr https://linuxfr.org/news.atom
        """.trimIndent()

        val extraites = SuggestionsSources.extraire(reponse)

        assertEquals(3, extraites.size)
        assertTrue(extraites[0].first.contains("Le Monde"))
        assertEquals("https://www.lemonde.fr/economie/rss_full.xml", extraites[0].second)
        assertEquals("https://next.ink/feed/", extraites[1].second)
        assertEquals("https://linuxfr.org/news.atom", extraites[2].second)
    }

    @Test
    fun `une adresse citee deux fois ne compte qu'une fois`() {
        val reponse = """
            https://exemple.fr/feed
            HTTPS://Exemple.fr/feed/
            http://exemple.fr/feed
        """.trimIndent()

        assertEquals(1, SuggestionsSources.extraire(reponse).size)
    }

    @Test
    fun `un texte sans adresse ne propose rien`() {
        assertEquals(0, SuggestionsSources.extraire("Je ne connais aucune source à proposer.").size)
    }

    @Test
    fun `la ponctuation collee a une adresse est retiree`() {
        val extraites = SuggestionsSources.extraire("Voir (https://exemple.fr/feed).")
        assertEquals("https://exemple.fr/feed", extraites.single().second)
    }

    @Test
    fun `les sources deja suivies ne sont jamais reproposees`() {
        val candidates = listOf(
            "Doublon" to "https://www.cnil.fr/fr/rss.xml",
            "Nouveau" to "https://www.anc.gouv.fr/rss.xml",
            "Doublon avec barre" to "http://www.cnil.fr/fr/rss.xml/",
        )
        val suivies = listOf("https://www.cnil.fr/fr/rss.xml")

        val aTester = SuggestionsSources.aTester(candidates, suivies)

        assertEquals(1, aTester.size)
        assertEquals("Nouveau", aTester.single().first)
    }

    @Test
    fun `le nombre d'adresses testees est plafonne`() {
        val candidates = (1..40).map { "Site $it" to "https://exemple$it.fr/feed" }
        assertEquals(
            SuggestionsSources.MAX_A_TESTER,
            SuggestionsSources.aTester(candidates, emptyList()).size,
        )
    }

    @Test
    fun `le bilan distingue les sources retenues des adresses ecartees`() {
        val propositions = listOf(
            SuggestionsSources.Proposition("A", "https://a.fr/feed", SuggestionsSources.Etat.PROPOSE, 10),
            SuggestionsSources.Proposition("B", "https://b.fr/x", SuggestionsSources.Etat.SANS_FLUX, detail = "HTTP 404"),
        )
        val resume = SuggestionsSources.resume(propositions)
        assertTrue(resume, resume.contains("1 source(s) vérifiée(s)"))
        assertTrue(resume, resume.contains("1 adresse(s) sans flux"))

        assertEquals(
            "Aucune des 1 adresses proposées ne répond un flux.",
            SuggestionsSources.resume(propositions.drop(1)),
        )
        assertTrue(SuggestionsSources.resume(emptyList()).contains("aucune adresse exploitable"))
    }

    @Test
    fun `la consigne rappelle le format et le nombre demandes`() {
        val consigne = SuggestionsSources.consigne(7)
        assertTrue(consigne, consigne.contains("JSON"))
        assertTrue(consigne, consigne.contains("7 sites"))
        // On demande l'adresse d'un site, pas celle d'un flux : le modèle invente
        // les adresses de flux (mesuré : 0 valide sur 13), pas les domaines.
        assertTrue(consigne, consigne.contains("adresse d'accueil"))
    }

    @Test
    fun `le contenu envoye au modele liste les sources suivies`() {
        val contenu = SuggestionsSources.contenu(
            listOf("CNIL" to "https://www.cnil.fr/fr/rss.xml", "BOFiP" to "http://bofip.impots.gouv.fr/x.xml"),
        )
        assertTrue(contenu, contenu.contains("CNIL <https://www.cnil.fr/fr/rss.xml>"))
        assertTrue(contenu, contenu.contains("BOFiP <http://bofip.impots.gouv.fr/x.xml>"))
    }
}
