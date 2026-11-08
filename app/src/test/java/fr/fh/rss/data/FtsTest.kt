package fr.fh.rss.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Construction de la requête plein texte. Le repli des accents et des
 * majuscules est assuré par le tokenizer unicode61 de l'index (voir
 * [fr.fh.rss.data.db.AppDatabase.MIGRATION_2_3]) : ici, on vérifie ce que la
 * requête envoie à SQLite.
 */
class FtsTest {

    @Test
    fun `le dernier mot est un prefixe`() {
        assertEquals("\"inflat*\"", Fts.query("inflat"))
        assertEquals("\"inflation\" \"tax*\"", Fts.query("inflation tax"))
    }

    @Test
    fun `seul le dernier mot est un prefixe`() {
        val requete = Fts.query("budget senat vote")
        assertEquals("\"budget\" \"senat\" \"vote*\"", requete)
        assertEquals(1, requete.count { it == '*' })
    }

    @Test
    fun `une etoile saisie ne s'ajoute pas a celle du prefixe`() {
        assertEquals("\"inflat*\"", Fts.query("inflat*"))
        assertEquals("\"inflat*\"", Fts.query("*inflat**"))
    }

    @Test
    fun `les accents sont conserves pour que le tokenizer les replie`() {
        // Retirer les accents ici ferait manquer « économie » à un index qui les replie.
        assertEquals("\"économie*\"", Fts.query("Économie"))
        assertEquals("\"élections\" \"présidentielles*\"", Fts.query("ÉLECTIONS présidentielles"))
    }

    @Test
    fun `une lettre accentuee decomposee ne coupe pas le mot`() {
        val decompose = "e\u0301conomie"
        assertEquals("\"économie*\"", Fts.query(decompose))
    }

    @Test
    fun `les caracteres speciaux ne cassent pas la requete`() {
        assertEquals("\"a\" \"b*\"", Fts.query("a - b"))
        assertEquals("\"or\" \"near*\"", Fts.query("\" OR NEAR(\""))
        assertEquals("\"\"", Fts.query("   "))
        assertEquals("\"\"", Fts.query("*-:\""))
    }

    @Test
    fun `huit mots au plus et le prefixe porte sur le dernier retenu`() {
        val requete = Fts.query("un deux trois quatre cinq six sept huit neuf dix")
        assertEquals("\"un\" \"deux\" \"trois\" \"quatre\" \"cinq\" \"six\" \"sept\" \"huit*\"", requete)
    }

    @Test
    fun `les chiffres comptent comme des mots`() {
        assertEquals("\"budget\" \"2027*\"", Fts.query("budget 2027"))
    }
}
