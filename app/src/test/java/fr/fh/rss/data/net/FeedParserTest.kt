package fr.fh.rss.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserTest {

    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"
             xmlns:dc="http://purl.org/dc/elements/1.1/"
             xmlns:media="http://search.yahoo.com/mrss/">
          <channel>
            <title>Le Monde Informatique</title>
            <link>https://exemple.fr</link>
            <item>
              <title>Une brève &amp; un test</title>
              <link>https://exemple.fr/article-1</link>
              <guid>tag:exemple.fr,2026:1</guid>
              <dc:creator>Jean Dupont</dc:creator>
              <pubDate>Tue, 27 Oct 2026 08:30:00 GMT</pubDate>
              <description><![CDATA[<p>Résumé court.</p>]]></description>
              <content:encoded><![CDATA[<p>Le corps complet de l'article.</p><img src="https://exemple.fr/photo.jpg">]]></content:encoded>
            </item>
            <item>
              <title>Deuxième article</title>
              <link>https://exemple.fr/article-2</link>
              <pubDate>Mon, 26 Oct 2026 10:00:00 +0100</pubDate>
              <description>Sans CDATA ici.</description>
              <media:content url="https://exemple.fr/image2.jpg" type="image/jpeg" />
            </item>
          </channel>
        </rss>
    """.trimIndent()

    @Test
    fun `rss 2 lisible avec deux articles`() {
        val flux = FeedParser.parse("https://exemple.fr/feed", rss)
        assertEquals("Le Monde Informatique", flux.title)
        assertEquals("rss", flux.kind)
        assertEquals(2, flux.items.size)
    }

    @Test
    fun `champs d'un article rss`() {
        val article = FeedParser.parse("https://exemple.fr/feed", rss).items.first()
        assertEquals("Une brève & un test", article.title)
        assertEquals("https://exemple.fr/article-1", article.url)
        assertEquals("tag:exemple.fr,2026:1", article.guid)
        assertEquals("Jean Dupont", article.author)
        assertNotNull(article.contentHtml)
        assertTrue(article.contentHtml!!.contains("corps complet"))
        assertEquals("https://exemple.fr/photo.jpg", article.imageUrl)
        // 27 octobre 2026 08:30 GMT
        assertEquals(1793089800000L, article.publishedAt)
    }

    @Test
    fun `image par media content`() {
        val article = FeedParser.parse("https://exemple.fr/feed", rss).items[1]
        assertEquals("https://exemple.fr/image2.jpg", article.imageUrl)
        assertNotNull(article.publishedAt)
    }

    @Test
    fun `flux atom`() {
        val atom = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Journal officiel</title>
              <link rel="alternate" href="https://exemple.gouv.fr"/>
              <entry>
                <title>Arrêté du 27 octobre</title>
                <id>urn:uuid:1234</id>
                <link rel="alternate" href="https://exemple.gouv.fr/arrete"/>
                <published>2026-10-27T09:15:00Z</published>
                <updated>2026-10-27T09:20:00Z</updated>
                <author><name>Direction générale</name></author>
                <summary>Résumé de l'arrêté.</summary>
                <content type="html">&lt;p&gt;Texte de l'arrêté.&lt;/p&gt;</content>
              </entry>
            </feed>
        """.trimIndent()

        val flux = FeedParser.parse("https://exemple.gouv.fr/atom", atom)
        assertEquals("atom", flux.kind)
        assertEquals("Journal officiel", flux.title)
        assertEquals(1, flux.items.size)
        val entree = flux.items.first()
        assertEquals("Arrêté du 27 octobre", entree.title)
        assertEquals("https://exemple.gouv.fr/arrete", entree.url)
        assertEquals("Direction générale", entree.author)
        assertEquals("urn:uuid:1234", entree.guid)
        assertEquals(1793092500000L, entree.publishedAt)
    }

    @Test
    fun `json feed`() {
        val json = """
            {
              "version": "https://jsonfeed.org/version/1.1",
              "title": "Blog technique",
              "home_page_url": "https://blog.exemple.fr",
              "items": [
                {
                  "id": "1",
                  "url": "https://blog.exemple.fr/kotlin",
                  "title": "Kotlin 2.1",
                  "content_html": "<p>Nouveautés du langage.</p>",
                  "date_published": "2026-10-27T07:00:00Z",
                  "author": { "name": "Fabrice" },
                  "image": "https://blog.exemple.fr/kotlin.png"
                },
                {
                  "id": "2",
                  "url": "https://blog.exemple.fr/android",
                  "title": "Android 16",
                  "content_text": "Texte brut.",
                  "date_published": "2026-10-26T07:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val flux = FeedParser.parse("https://blog.exemple.fr/feed.json", json)
        assertEquals("json", flux.kind)
        assertEquals("Blog technique", flux.title)
        assertEquals(2, flux.items.size)
        assertEquals("Fabrice", flux.items.first().author)
        assertEquals("https://blog.exemple.fr/kotlin.png", flux.items.first().imageUrl)
        assertNotNull(flux.items[1].contentHtml)
    }

    @Test
    fun `flux mal forme ne leve pas d'exception`() {
        val casse = "<rss><channel><title>Coupé"
        val flux = FeedParser.parse("https://exemple.fr/feed", casse)
        assertEquals("Coupé", flux.title)
        assertTrue(flux.items.isEmpty())
    }

    @Test
    fun `corps vide`() {
        val flux = FeedParser.parse("https://exemple.fr/feed", "")
        assertEquals("exemple.fr", flux.title)
        assertTrue(flux.items.isEmpty())
    }

    @Test
    fun `article sans lien utilise le guid`() {
        val sansLien = """
            <rss><channel><title>Sans lien</title>
              <item><title>Note</title><guid>abc-123</guid><pubDate>Tue, 27 Oct 2026 08:30:00 GMT</pubDate></item>
            </channel></rss>
        """.trimIndent()
        val article = FeedParser.parse("https://exemple.fr/feed", sansLien).items.first()
        assertEquals("abc-123", article.guid)
        assertNull(article.url)
    }

    @Test
    fun `dates rfc822 et iso 8601`() {
        assertEquals(1793089800000L, Dates.parse("Tue, 27 Oct 2026 08:30:00 GMT"))
        assertEquals(1793092500000L, Dates.parse("2026-10-27T09:15:00Z"))
        assertEquals(null, Dates.parse("pas une date"))
        assertEquals(null, Dates.parse(null))
    }
}
