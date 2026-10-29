package fr.fh.rss.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlTest {

    private val opml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <opml version="2.0">
          <head><title>Mes sources</title></head>
          <body>
            <outline text="Presse" title="Presse">
              <outline type="rss" text="Le Monde" title="Le Monde"
                       xmlUrl="https://lemonde.fr/rss/une.xml" htmlUrl="https://lemonde.fr" />
              <outline type="rss" text="Les Échos" xmlUrl="https://lesechos.fr/rss.xml" />
              <outline text="Tech" title="Tech">
                <outline type="rss" text="Next" xmlUrl="https://next.ink/feed/" />
              </outline>
            </outline>
            <outline type="rss" text="Sans dossier" xmlUrl="https://exemple.fr/feed" />
          </body>
        </opml>
    """.trimIndent()

    @Test
    fun `import avec dossiers imbriques`() {
        val entrees = Opml.parse(opml)
        assertEquals(4, entrees.size)

        val monde = entrees.first { it.xmlUrl.contains("lemonde") }
        assertEquals("Le Monde", monde.titre)
        assertEquals("Presse", monde.dossier)
        assertEquals("https://lemonde.fr", monde.htmlUrl)

        val next = entrees.first { it.xmlUrl.contains("next.ink") }
        assertEquals("Tech", next.dossier)

        val sansDossier = entrees.first { it.xmlUrl.contains("exemple.fr") }
        assertEquals(null, sansDossier.dossier)
    }

    @Test
    fun `import tolere les attributs en minuscules`() {
        val variante = """
            <opml version="1.0"><body>
              <outline text="Bas de casse" xmlurl="https://exemple.fr/rss" />
            </body></opml>
        """.trimIndent()
        val entrees = Opml.parse(variante)
        assertEquals(1, entrees.size)
        assertEquals("https://exemple.fr/rss", entrees.first().xmlUrl)
    }

    @Test
    fun `export puis relecture conserve sources et dossiers`() {
        val xml = Opml.export(
            listOf(
                null to listOf("Sans dossier" to "https://a.fr/feed"),
                "Presse" to listOf(
                    "Le Monde" to "https://lemonde.fr/rss/une.xml",
                    "Guillemets \"ici\"" to "https://b.fr/feed",
                ),
            ),
        )
        assertTrue(xml.startsWith("<?xml"))
        val relu = Opml.parse(xml)
        assertEquals(3, relu.size)
        assertEquals("Presse", relu.first { it.xmlUrl.contains("lemonde") }.dossier)
        assertEquals("Presse", relu.first { it.xmlUrl.contains("b.fr") }.dossier)
        assertEquals(null, relu.first { it.xmlUrl.contains("a.fr") }.dossier)
        assertTrue(relu.any { it.titre.contains("Guillemets") })
    }

    @Test
    fun `opml vide ou illisible`() {
        assertTrue(Opml.parse("").isEmpty())
        assertTrue(Opml.parse("pas du tout du xml").isEmpty())
    }

    @Test
    fun `requete fts protegee des caracteres speciaux`() {
        assertEquals("\"android\" \"kotlin\"", Fts.query("Android Kotlin"))
        assertEquals("\"android\"", Fts.query("android*"))
        assertEquals("\"a\" \"b\"", Fts.query("a - b"))
        assertEquals("\"\"", Fts.query("   "))
        // 8 mots maximum
        val longue = Fts.query("un deux trois quatre cinq six sept huit neuf dix")
        assertEquals(8, Regex("\"").findAll(longue).count() / 2)
    }

    @Test
    fun `empreinte stable et courte`() {
        val a = empreinte("titre|https://exemple.fr")
        val b = empreinte("titre|https://exemple.fr")
        val c = empreinte("autre|https://exemple.fr")
        assertEquals(a, b)
        assertFalse(a == c)
        assertEquals(32, a.length)
    }
}
