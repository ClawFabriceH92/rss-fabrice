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
    fun `la base est en version 3 et exporte son schema`() {
        val db = AppDatabase::class.java.getAnnotation(androidx.room.Database::class.java)
        // @Database est conservée à l'exécution pour le compilateur seulement : repli sur le source.
        if (db != null) {
            assertEquals(3, db.version)
            assertTrue(db.exportSchema)
        } else {
            assertTrue(source.contains("version = 3,"))
            assertTrue(source.contains("exportSchema = true"))
        }
    }

    @Test
    fun `les migrations 1 vers 2 et 2 vers 3 sont declarees et enregistrees`() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)
        assertTrue(source.contains(".addMigrations(MIGRATION_1_2, MIGRATION_2_3)"))
    }

    private fun entites(version: Int): Map<String, JSONObject> {
        val json = JSONObject(
            fichier("schemas/fr.fh.rss.data.db.AppDatabase/$version.json").readText(),
        )
        val base = json.getJSONObject("database")
        assertEquals(version, base.getInt("version"))
        val liste = base.getJSONArray("entities")
        return (0 until liste.length()).associate {
            liste.getJSONObject(it).getString("tableName") to liste.getJSONObject(it)
        }
    }

    private fun colonnes(table: JSONObject): List<String> = table.getJSONArray("fields").let { a ->
        (0 until a.length()).map { a.getJSONObject(it).getString("columnName") }
    }

    @Test
    fun `le schema exporte de la version 3 est present et decrit l'index unicode61`() {
        val tables = entites(3)
        assertEquals(setOf("folders", "feeds", "articles", "articles_fts"), tables.keys)

        val fts = tables.getValue("articles_fts")
        assertEquals("unicode61", fts.getJSONObject("ftsOptions").getString("tokenizer"))
        assertEquals(listOf("title", "body"), colonnes(fts).filter { it != "rowid" })

        val colonnesArticles = colonnes(tables.getValue("articles"))
        assertTrue("texteCompletEssaiLe" in colonnesArticles && "texteCompletEchec" in colonnesArticles)
    }

    @Test
    fun `le schema de la version 2 reste versionne pour la migration`() {
        val fts = entites(2).getValue("articles_fts")
        assertEquals("simple", fts.getJSONObject("ftsOptions").getString("tokenizer"))
    }

    @Test
    fun `la migration 2 vers 3 recree l'index comme le schema 3 l'attend et le reindexe`() {
        val sql = AppDatabase.SQL_MIGRATION_2_3
        val creation = sql.single { it.startsWith("CREATE VIRTUAL TABLE") }
        val attendu = entites(3).getValue("articles_fts").getString("createSql")
            .replace("\${TABLE_NAME}", "articles_fts")
        // Même définition que celle de Room : sinon la validation du schéma échouerait au premier lancement.
        assertEquals(attendu, creation)

        // Ordre : l'ancien index disparaît avant d'être recréé, puis il est rempli depuis articles.
        val rang = { debut: String -> sql.indexOfFirst { it.startsWith(debut) } }
        assertTrue(rang("DROP TABLE IF EXISTS `articles_fts`") < rang("CREATE VIRTUAL TABLE"))
        assertTrue(rang("CREATE VIRTUAL TABLE") < rang("INSERT INTO `articles_fts`"))
        val insertion = sql.single { it.startsWith("INSERT INTO `articles_fts`") }
        assertTrue(insertion, insertion.contains("FROM `articles`"))
        assertTrue(insertion, insertion.contains("COALESCE(`textContent`, '')"))
    }

    @Test
    fun `la migration 2 vers 3 ne supprime ni ne vide la table des articles`() {
        val sql = AppDatabase.SQL_MIGRATION_2_3
        // Seul l'index plein texte est supprimé, jamais la table des articles.
        assertEquals(
            listOf("DROP TABLE IF EXISTS `articles_fts`"),
            sql.filter { it.uppercase().startsWith("DROP") },
        )
        assertTrue(sql.none { it.uppercase().startsWith("DELETE") || it.uppercase().startsWith("UPDATE") })
        // Les colonnes ajoutées doivent accepter les lignes existantes.
        val ajouts = AppDatabase.SQL_MIGRATION_2_3.filter { it.startsWith("ALTER TABLE `articles` ADD COLUMN") }
        assertEquals(2, ajouts.size)
        assertTrue(ajouts.all { !it.contains("NOT NULL") || it.contains("DEFAULT") })
    }

    @Test
    fun `la migration 2 vers 3 n'a rien de non deterministe`() {
        val tout = AppDatabase.SQL_MIGRATION_2_3.joinToString("\n").lowercase()
        for (interdit in listOf("random", "datetime", "current_timestamp", "strftime", "now(")) {
            assertFalse(interdit, tout.contains(interdit))
        }
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

    private val requetes: String
        get() = fichier("src/main/java/fr/fh/rss/data/db/Daos.kt").readText()

    /** Texte SQL de la requête @Query placée juste au-dessus de la fonction [nom]. */
    private fun sqlDe(nom: String): String {
        val trouve = Regex(
            "@Query\\(\\s*((?:\"[^\"]*\"\\s*\\+?\\s*)+),?\\s*\\)\\s*suspend fun $nom\\(",
        ).find(requetes)
        assertTrue("requête introuvable pour $nom", trouve != null)
        return Regex("\"([^\"]*)\"").findAll(trouve!!.groupValues[1]).joinToString("") { it.groupValues[1] }
    }

    @Test
    fun `supprimer les articles d'une source epargne les favoris`() {
        val sql = sqlDe("deleteArticles")
        assertTrue(sql, sql.contains("DELETE FROM articles"))
        assertTrue("les favoris doivent survivre : $sql", sql.contains("isStarred = 0"))
        assertTrue(sqlDe("clearFeed").contains("isStarred = 0"))
    }

    @Test
    fun `aucune requete ne supprime d'articles sans epargner les favoris`() {
        // Seule la requête explicite de nettoyage par id (index plein texte) n'est pas sur « articles ».
        val suppressions = Regex("\"DELETE FROM articles (?!_fts)[^\"]*\"").findAll(requetes).map { it.value }.toList()
        assertTrue(suppressions.isNotEmpty())
        for (sql in suppressions) {
            // Le nettoyage de l'index FTS et la suppression de la ligne de source ne concernent pas les articles.
            assertTrue("favoris non épargnés : $sql", sql.contains("isStarred = 0"))
        }
    }

    @Test
    fun `l'apercu de purge reprend la meme regle que la purge`() {
        val purge = sqlDe("trim")
        val apercu = sqlDe("compterPurge")
        assertTrue(purge.contains("isStarred = 0") && apercu.contains("isStarred = 0"))
        assertEquals(
            purge.substringAfter("id NOT IN"),
            apercu.substringAfter("id NOT IN"),
        )
        assertTrue(sqlDe("compterPurgeNonLus").contains("isRead = 0"))
    }
}
