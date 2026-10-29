package fr.fh.rss

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Garde-fou vécu sur une autre application : si le dossier où l'APK de mise à
 * jour est écrit n'est pas déclaré dans file_paths.xml, FileProvider lève
 * « Failed to find configured root » et le bouton Installer ne fait rien.
 * Le dossier de travail des tests unitaires est celui du module `app`.
 */
class FilePathsTest {

    private val fichier = File("src/main/res/xml/file_paths.xml")

    @Test
    fun `le fichier existe et est un xml valide`() {
        assertTrue("file_paths.xml introuvable : ${fichier.absolutePath}", fichier.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fichier)
        assertTrue(doc.documentElement.tagName == "paths")
    }

    @Test
    fun `le dossier des mises a jour est declare`() {
        val contenu = fichier.readText()
        assertTrue(
            "files-path \"updates/\" manquant : l'installation de l'APK échouerait en silence",
            contenu.contains("path=\"updates/\""),
        )
        assertTrue("files-path \"exports/\" manquant", contenu.contains("path=\"exports/\""))
    }
}
