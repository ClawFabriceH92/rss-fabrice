package fr.fh.rss.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** Plafond de taille, type de page, redirections et encodage des réponses HTTP. */
class CorpsHttpTest {

    // ------------------------------------------------------------- plafond

    @Test
    fun `une reponse sous le plafond est lue en entier`() {
        val octets = ByteArray(10_000) { 'a'.code.toByte() }
        val lu = CorpsHttp.lire(ByteArrayInputStream(octets), max = 10_000)
        assertEquals(10_000, lu.size)
    }

    @Test
    fun `une reponse au dela du plafond leve une erreur explicite`() {
        val octets = ByteArray(10_001)
        try {
            CorpsHttp.lire(ByteArrayInputStream(octets), max = 10_000)
            fail("le plafond aurait dû lever une erreur")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("trop volumineuse"))
        }
    }

    @Test
    fun `la lecture s'arrete au plafond sans tout consommer`() {
        // Flux sans fin : sans plafond, ce test ne finirait jamais (ou saturerait la mémoire).
        var lus = 0L
        val infini = object : InputStream() {
            override fun read(): Int = 'x'.code.also { lus++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                java.util.Arrays.fill(b, off, off + len, 'x'.code.toByte())
                lus += len
                return len
            }
        }
        try {
            CorpsHttp.lire(infini, max = 50_000)
            fail("le plafond aurait dû lever une erreur")
        } catch (e: IOException) {
            assertTrue("lecture bornée : $lus octets", lus <= 50_000 + 8 * 1024)
        }
    }

    @Test
    fun `une longueur annoncee trop grande est refusee sans rien lire`() {
        val sentinelle = object : InputStream() {
            override fun read(): Int = throw AssertionError("rien ne doit être lu")
        }
        try {
            CorpsHttp.lire(sentinelle, CorpsHttp.MAX_FLUX, longueurAnnoncee = 100L * 1024 * 1024)
            fail("la longueur annoncée aurait dû être refusée")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("5 Mo"))
        }
    }

    @Test
    fun `les plafonds sont de 5 Mo pour un flux et 3 Mo pour une page`() {
        assertEquals(5 * 1024 * 1024, CorpsHttp.MAX_FLUX)
        assertEquals(3 * 1024 * 1024, CorpsHttp.MAX_PAGE)
    }

    // ------------------------------------------------------- type de la page

    @Test
    fun `seules les pages web sont acceptees pour le lecteur`() {
        assertTrue(CorpsHttp.typePageAcceptable("text/html; charset=utf-8"))
        assertTrue(CorpsHttp.typePageAcceptable("TEXT/HTML"))
        assertTrue(CorpsHttp.typePageAcceptable("application/xhtml+xml"))
        assertTrue(CorpsHttp.typePageAcceptable(null))
        assertTrue(CorpsHttp.typePageAcceptable(""))
        assertFalse(CorpsHttp.typePageAcceptable("application/pdf"))
        assertFalse(CorpsHttp.typePageAcceptable("audio/mpeg"))
        assertFalse(CorpsHttp.typePageAcceptable("image/png"))
        assertFalse(CorpsHttp.typePageAcceptable("application/octet-stream"))
    }

    // ---------------------------------------------------------- redirections

    @Test
    fun `une redirection est permanente si chaque etape l'est`() {
        assertTrue(CorpsHttp.redirectionPermanente(listOf(301)))
        assertTrue(CorpsHttp.redirectionPermanente(listOf(301, 308)))
        assertFalse(CorpsHttp.redirectionPermanente(emptyList()))
        assertFalse(CorpsHttp.redirectionPermanente(listOf(302)))
        assertFalse(CorpsHttp.redirectionPermanente(listOf(301, 302)))
        assertFalse(CorpsHttp.redirectionPermanente(listOf(307)))
    }

    // -------------------------------------------------------------- encodage

    private val latin1 = Charsets.ISO_8859_1

    private fun fluxLatin1(prologue: String) = (
        "$prologue<rss version=\"2.0\"><channel><title>Économie à Noël</title>" +
            "<item><title>Été « brûlant »</title><link>https://exemple.fr/ete</link></item>" +
            "</channel></rss>"
        ).toByteArray(latin1)

    @Test
    fun `un flux ISO-8859-1 declare dans le prologue garde ses accents`() {
        val octets = fluxLatin1("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>")
        val texte = CorpsHttp.decoder(octets, "application/rss+xml")
        val flux = FeedParser.parse("https://exemple.fr/feed", texte)
        assertEquals("Économie à Noël", flux.title)
        assertEquals("Été « brûlant »", flux.items.single().title)
    }

    @Test
    fun `le charset de l'en-tete l'emporte sur le prologue`() {
        val octets = fluxLatin1("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        val texte = CorpsHttp.decoder(octets, "text/xml; charset=ISO-8859-1")
        assertTrue(texte.contains("Économie à Noël"))
    }

    @Test
    fun `sans declaration le flux est lu en UTF-8`() {
        val texte = CorpsHttp.decoder("<rss><channel><title>Économie</title></channel></rss>".toByteArray(Charsets.UTF_8))
        assertTrue(texte.contains("Économie"))
    }

    @Test
    fun `le BOM UTF-8 n'entre pas dans le texte`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val texte = CorpsHttp.decoder(bom + "<rss/>".toByteArray(Charsets.UTF_8))
        assertEquals("<rss/>", texte)
    }

    @Test
    fun `une page HTML ISO-8859-1 est reconnue par sa balise meta`() {
        val html = "<html><head><meta charset=\"iso-8859-1\"></head><body>Café</body></html>".toByteArray(latin1)
        assertTrue(CorpsHttp.decoder(html, "text/html").contains("Café"))
    }

    @Test
    fun `un encodage inconnu retombe sur UTF-8 sans planter`() {
        val octets = "<?xml version=\"1.0\" encoding=\"inconnu-9\"?><rss/>".toByteArray(Charsets.UTF_8)
        assertTrue(CorpsHttp.decoder(octets).endsWith("<rss/>"))
    }
}
