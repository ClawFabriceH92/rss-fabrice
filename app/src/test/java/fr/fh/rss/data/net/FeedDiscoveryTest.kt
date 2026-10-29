package fr.fh.rss.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedDiscoveryTest {

    @Test
    fun `detection par balise alternate`() {
        val html = """
            <html><head>
              <link rel="alternate" type="application/rss+xml" title="Flux" href="/feed.xml">
              <link rel="alternate" type="application/atom+xml" href="https://exemple.fr/atom.xml">
              <link rel="stylesheet" href="/style.css">
              <link rel="alternate" type="text/html" hreflang="en" href="/en/">
            </head><body>Bonjour</body></html>
        """.trimIndent()
        val liens = FeedDiscovery.fromHtml(html, "https://exemple.fr/blog/")
        assertEquals(2, liens.size)
        assertEquals("https://exemple.fr/feed.xml", liens[0])
        assertEquals("https://exemple.fr/atom.xml", liens[1])
    }

    @Test
    fun `aucune balise donne une liste vide`() {
        assertTrue(FeedDiscovery.fromHtml("<html><body>rien</body></html>", "https://a.fr").isEmpty())
    }

    @Test
    fun `reconnaissance d'un flux`() {
        assertTrue(FeedDiscovery.looksLikeFeed("<?xml version=\"1.0\"?><rss version=\"2.0\">"))
        assertTrue(FeedDiscovery.looksLikeFeed("<feed xmlns=\"http://www.w3.org/2005/Atom\">"))
        assertTrue(FeedDiscovery.looksLikeFeed("{\"version\":\"https://jsonfeed.org/version/1.1\",\"items\":[]}"))
        assertFalse(FeedDiscovery.looksLikeFeed("<html><head><title>Site</title></head></html>"))
        assertFalse(FeedDiscovery.looksLikeFeed("{\"status\":\"ok\"}"))
    }

    @Test
    fun `adresses de repli`() {
        val candidats = FeedDiscovery.candidates("https://exemple.fr/blog/article")
        assertTrue(candidats.contains("https://exemple.fr/feed"))
        assertTrue(candidats.contains("https://exemple.fr/rss.xml"))
        assertEquals(7, candidats.size)
    }

    @Test
    fun `normalisation de la saisie`() {
        assertEquals("https://exemple.fr", FeedDiscovery.normalizeUrl("exemple.fr"))
        assertEquals("https://exemple.fr/feed", FeedDiscovery.normalizeUrl("https://exemple.fr/feed"))
        assertEquals("http://exemple.fr", FeedDiscovery.normalizeUrl("http://exemple.fr"))
        assertEquals("https://exemple.fr", FeedDiscovery.normalizeUrl("feed://exemple.fr"))
        assertEquals("https://exemple.fr", FeedDiscovery.normalizeUrl("  exemple.fr  "))
        assertEquals("", FeedDiscovery.normalizeUrl("   "))
    }

    @Test
    fun `extraction du texte principal`() {
        val page = """
            <html><body>
              <nav><a href="/">Accueil</a><a href="/blog">Blog</a></nav>
              <article>
                <h1>Titre de l'article</h1>
                <p>${"Premier paragraphe avec du texte utile. ".repeat(6)}</p>
                <p>${"Deuxième paragraphe, un peu plus long encore. ".repeat(6)}</p>
                <p><img src="/photo.jpg" alt="photo"></p>
              </article>
              <footer>Mentions légales</footer>
            </body></html>
        """.trimIndent()

        val extrait = ReaderExtractor.extract(page, "https://exemple.fr/article")
        assertTrue(extrait != null)
        assertTrue(extrait!!.texte.contains("Premier paragraphe"))
        assertFalse(extrait.texte.contains("Mentions légales"))
        assertTrue(extrait.html.contains("https://exemple.fr/photo.jpg"))
    }
}
