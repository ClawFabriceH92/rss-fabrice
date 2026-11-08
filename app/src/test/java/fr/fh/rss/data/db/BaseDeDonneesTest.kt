package fr.fh.rss.data.db

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde-fous de la base Room, vérifiables sur JVM : version, schéma exporté et
 * absence de filet destructif. Le comportement d'une vraie migration demande
 * un appareil (MigrationTestHelper) : non couvert ici.
 */
class BaseDeDonneesTest {

    /** Le répertoire de travail des tests est le module `app`. */
    private fun fichier(chemin: String): File {
        val direct = File(chemin)
        return if (direct.exists()) direct else File("app/$chemin")
    }

    private val source: String
        get() = fichier("src/main/java/fr/fh/rss/data/db/AppDatabase.kt").readText()

    @Test
    fun `aucune migration destructive n'est autorisee`() {
        // Le mot peut apparaître dans un commentaire : on ne regarde que le code.
        val code = source.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") }
        assertFalse(code.any { it.contains("fallbackToDestructiveMigration") })
    }

    @Test
    fun `la base reste en version 2 et exporte son schema`() {
        val db = AppDatabase::class.java.getAnnotation(androidx.room.Database::class.java)
        // @Database est conservée à l'exécution pour le compilateur seulement : repli sur le source.
        if (db != null) {
            assertEquals(2, db.version)
            assertTrue(db.exportSchema)
        } else {
            assertTrue(source.contains("version = 2,"))
            assertTrue(source.contains("exportSchema = true"))
        }
    }

    @Test
    fun `la migration 1 vers 2 est declaree`() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun `le schema exporte de la version 2 decrit les quatre tables`() {
        val json = JSONObject(
            fichier("schemas/fr.fh.rss.data.db.AppDatabase/2.json").readText(),
        )
        val base = json.getJSONObject("database")
        assertEquals(2, base.getInt("version"))

        val tables = base.getJSONArray("entities").let { a ->
            (0 until a.length()).associate { a.getJSONObject(it).getString("tableName") to a.getJSONObject(it) }
        }
        assertEquals(setOf("folders", "feeds", "articles", "articles_fts"), tables.keys)

        val colonnesFeeds = tables.getValue("feeds").getJSONArray("fields").let { a ->
            (0 until a.length()).map { a.getJSONObject(it).getString("columnName") }
        }
        // Colonnes ajoutées par MIGRATION_1_2.
        assertTrue("errorSince" in colonnesFeeds && "errorCount" in colonnesFeeds)

        val index = tables.getValue("articles").getJSONArray("indices")
        val unique = (0 until index.length()).map { index.getJSONObject(it) }
            .first { it.getBoolean("unique") }
        assertEquals(listOf("feedId", "guid"), unique.getJSONArray("columnNames").let { a ->
            (0 until a.length()).map { a.getString(it) }
        })
    }
}
