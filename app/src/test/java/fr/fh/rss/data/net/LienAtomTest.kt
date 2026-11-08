package fr.fh.rss.data.net

import fr.fh.rss.data.net.FeedParser.LienAtom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Choix du lien d'un article ou d'un site dans un flux Atom. */
class LienAtomTest {

    private fun lien(rel: String?, href: String) = LienAtom(rel, href)

    @Test
    fun `alternate l'emporte meme s'il vient apres un lien de commentaires`() {
        val choisi = FeedParser.choisirLienAtom(
            listOf(
                lien("replies", "https://blog.exemple.fr/feeds/1/comments/default"),
                lien("edit", "https://www.blogger.com/feeds/1/posts/default/1"),
                lien("alternate", "https://blog.exemple.fr/2026/10/article.html"),
            ),
        )
        assertEquals("https://blog.exemple.fr/2026/10/article.html", choisi)
    }

    @Test
    fun `un lien sans rel vaut alternate`() {
        val choisi = FeedParser.choisirLienAtom(
            listOf(lien("self", "https://exemple.fr/feed"), lien(null, "https://exemple.fr/a")),
        )
        assertEquals("https://exemple.fr/a", choisi)
    }

    @Test
    fun `sans alternate on prend le premier lien http utile`() {
        val choisi = FeedParser.choisirLienAtom(
            listOf(
                lien("self", "https://exemple.fr/feed"),
                lien("replies", "https://exemple.fr/commentaires"),
                lien("enclosure", "https://exemple.fr/audio.mp3"),
                lien("related", "https://exemple.fr/lie"),
            ),
        )
        assertEquals("https://exemple.fr/lie", choisi)
    }

    @Test
    fun `replies edit enclosure et self ne sont jamais retenus`() {
        val choisi = FeedParser.choisirLienAtom(
            listOf(
                lien("replies", "https://exemple.fr/c"),
                lien("edit", "https://exemple.fr/e"),
                lien("enclosure", "https://exemple.fr/m.mp3"),
                lien("self", "https://exemple.fr/s"),
            ),
        )
        assertNull(choisi)
    }

    @Test
    fun `un lien non http est ecarte`() {
        assertNull(FeedParser.choisirLienAtom(listOf(lien("alternate", "/relatif"), lien(null, "mailto:a@b.fr"))))
    }

    private fun entree(corps: String, racine: String = "") = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" $racine>
          <title>Blog</title>
          <link rel="self" href="https://blog.exemple.fr/feed.atom"/>
          <link rel="alternate" href="https://blog.exemple.fr/"/>
          $corps
        </feed>
    """.trimIndent()

    @Test
    fun `un flux Blogger ouvre l'article et non le flux de commentaires`() {
        val flux = FeedParser.parse(
            "https://blog.exemple.fr/feed.atom",
            entree(
                """
                <entry>
                  <id>tag:blogger.com,1999:blog-1.post-2</id>
                  <title>Mon billet</title>
                  <link rel="replies" type="application/atom+xml" href="https://blog.exemple.fr/feeds/2/comments/default"/>
                  <link rel="replies" type="text/html" href="https://blog.exemple.fr/2026/10/billet.html#comment-form"/>
                  <link rel="edit" href="https://www.blogger.com/feeds/1/posts/default/2"/>
                  <link rel="alternate" type="text/html" href="https://blog.exemple.fr/2026/10/billet.html"/>
                </entry>
                """.trimIndent(),
            ),
        )
        assertEquals("https://blog.exemple.fr/2026/10/billet.html", flux.items.single().url)
        assertEquals("https://blog.exemple.fr/", flux.siteUrl)
    }

    @Test
    fun `un lien relatif sans base n'est pas retenu`() {
        val flux = FeedParser.parse(
            "https://blog.exemple.fr/feed.atom",
            entree("""<entry><id>urn:uuid:1</id><title>Relatif</title><link href="/2026/billet"/></entry>"""),
        )
        assertNull(flux.items.single().url)
    }

    @Test
    fun `un lien relatif est resolu contre xml base`() {
        val flux = FeedParser.parse(
            "https://blog.exemple.fr/feed.atom",
            entree(
                """<entry xml:base="https://blog.exemple.fr/"><id>urn:uuid:1</id><title>Base</title><link href="2026/billet"/></entry>""",
            ),
        )
        assertEquals("https://blog.exemple.fr/2026/billet", flux.items.single().url)
    }
}
