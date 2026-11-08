package fr.fh.rss.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** Lecture du fichier OPML choisi : tout ce qui est illisible donne `null`, jamais une exception. */
class LectureOpmlTest {

    private fun flux(texte: String, jeu: java.nio.charset.Charset = Charsets.UTF_8) =
        ByteArrayInputStream(texte.toByteArray(jeu))

    @Test
    fun `un fichier UTF-8 est lu tel quel`() {
        val xml = "<opml><body><outline text=\"Économie\" xmlUrl=\"https://a.fr/rss\"/></body></opml>"
        assertEquals(xml, LectureOpml.lire(flux(xml)))
    }

    @Test
    fun `le BOM UTF-8 est retire`() {
        val lu = LectureOpml.lire(flux("\uFEFF<opml/>"))
        assertEquals("<opml/>", lu)
    }

    @Test
    fun `l'encodage declare dans le prologue est respecte`() {
        val xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><opml text=\"Écolo\"/>"
        assertEquals(xml, LectureOpml.lire(flux(xml, Charsets.ISO_8859_1)))
    }

    @Test
    fun `un encodage declare inconnu retombe sur UTF-8`() {
        val xml = "<?xml version=\"1.0\" encoding=\"inconnu-9\"?><opml/>"
        assertEquals(xml, LectureOpml.lire(flux(xml)))
    }

    @Test
    fun `un flux absent est illisible`() {
        assertNull(LectureOpml.lire(null))
    }

    @Test
    fun `un fichier vide ou blanc est illisible`() {
        assertNull(LectureOpml.lire(flux("")))
        assertNull(LectureOpml.lire(flux("  \n\t ")))
    }

    @Test
    fun `un fichier trop gros est refuse`() {
        assertNull(LectureOpml.lire(flux("x".repeat(101)), tailleMax = 100))
        assertNotNull(LectureOpml.lire(flux("x".repeat(100)), tailleMax = 100))
    }

    @Test
    fun `une erreur de lecture est illisible, pas une exception`() {
        val casse = object : InputStream() {
            override fun read(): Int = throw IOException("support retiré")
            override fun read(b: ByteArray): Int = throw IOException("support retiré")
        }
        assertNull(LectureOpml.lire(casse))
    }

    @Test
    fun `un refus d'acces au document est illisible`() {
        val refuse = object : InputStream() {
            override fun read(): Int = throw SecurityException("permission retirée")
            override fun read(b: ByteArray): Int = throw SecurityException("permission retirée")
        }
        assertNull(LectureOpml.lire(refuse))
    }

    @Test
    fun `le flux est toujours referme`() {
        var ferme = false
        val suivi = object : ByteArrayInputStream("<opml/>".toByteArray()) {
            override fun close() {
                ferme = true
                super.close()
            }
        }
        LectureOpml.lire(suivi)
        assertEquals(true, ferme)
    }
}
