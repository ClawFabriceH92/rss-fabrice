package fr.fh.rss.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Purge des exports OPML accumulés dans files/exports. */
class ExportsOpmlTest {

    @get:Rule
    val dossierTemporaire = TemporaryFolder()

    /** Crée un fichier daté de [age] minutes dans le passé. */
    private fun fichier(nom: String, ageMinutes: Long = 0): File =
        dossierTemporaire.newFile(nom).also {
            it.writeText("<opml/>")
            it.setLastModified(System.currentTimeMillis() - ageMinutes * 60_000L)
        }

    @Test
    fun `seuls les plus recents sont conserves`() {
        val anciens = (1..5).map { fichier(ExportsOpml.nomFichier("2026110$it-0900"), ageMinutes = 1000L - it) }
        val recent1 = fichier(ExportsOpml.nomFichier("20261108-0900"), ageMinutes = 10)
        val recent2 = fichier(ExportsOpml.nomFichier("20261108-1000"), ageMinutes = 5)

        val supprimes = ExportsOpml.purger(dossierTemporaire.root)

        assertEquals(5, supprimes)
        assertTrue(anciens.none { it.exists() })
        assertTrue(recent1.exists())
        assertTrue(recent2.exists())
    }

    @Test
    fun `rien n'est supprime sous le nombre conserve`() {
        val seul = fichier(ExportsOpml.nomFichier("20261108-0900"))

        assertEquals(0, ExportsOpml.purger(dossierTemporaire.root))
        assertTrue(seul.exists())
    }

    @Test
    fun `les autres fichiers du dossier ne sont jamais touches`() {
        val autre = fichier("notes.txt", ageMinutes = 99_999)
        val opmlManuel = fichier("mes-flux.opml", ageMinutes = 99_999)
        val sansSuffixe = fichier("sources-rss-20261101-0900.xml", ageMinutes = 99_999)
        repeat(4) { fichier(ExportsOpml.nomFichier("2026110$it-0900"), ageMinutes = 100L - it) }

        ExportsOpml.purger(dossierTemporaire.root)

        assertTrue(autre.exists())
        assertTrue(opmlManuel.exists())
        assertTrue(sansSuffixe.exists())
        assertEquals(2, dossierTemporaire.root.listFiles()!!.count { ExportsOpml.estUnExport(it) })
    }

    @Test
    fun `a date egale le nom horodate departage`() {
        val maintenant = System.currentTimeMillis()
        val a = dossierTemporaire.newFile(ExportsOpml.nomFichier("20261101-0900")).also { it.setLastModified(maintenant) }
        val b = dossierTemporaire.newFile(ExportsOpml.nomFichier("20261102-0900")).also { it.setLastModified(maintenant) }
        val c = dossierTemporaire.newFile(ExportsOpml.nomFichier("20261103-0900")).also { it.setLastModified(maintenant) }

        assertEquals(1, ExportsOpml.purger(dossierTemporaire.root))

        assertFalse(a.exists())
        assertTrue(b.exists())
        assertTrue(c.exists())
    }

    @Test
    fun `conserver zero supprime tous les exports`() {
        repeat(3) { fichier(ExportsOpml.nomFichier("2026110$it-0900")) }

        assertEquals(3, ExportsOpml.purger(dossierTemporaire.root, conserves = 0))
    }

    @Test
    fun `un dossier absent ou un chemin de fichier ne leve pas d'erreur`() {
        assertEquals(0, ExportsOpml.purger(File(dossierTemporaire.root, "inexistant")))
        assertEquals(0, ExportsOpml.purger(fichier("seul.txt")))
    }

    @Test
    fun `un repertoire au nom d'export n'est pas pris pour un export`() {
        val faux = dossierTemporaire.newFolder(ExportsOpml.nomFichier("20261101-0900"))

        assertEquals(0, ExportsOpml.purger(dossierTemporaire.root, conserves = 0))
        assertTrue(faux.exists())
    }
}
