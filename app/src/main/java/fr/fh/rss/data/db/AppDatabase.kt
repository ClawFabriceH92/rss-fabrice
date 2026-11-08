package fr.fh.rss.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, FeedEntity::class, ArticleEntity::class, ArticleFts::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao

    companion object {

        /**
         * Suivi des sources en échec : début de la série et nombre d'échecs
         * consécutifs. Migration écrite à la main pour ne pas perdre les
         * articles déjà collectés sur le téléphone.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE feeds ADD COLUMN errorSince INTEGER")
                db.execSQL("ALTER TABLE feeds ADD COLUMN errorCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Instructions de [MIGRATION_2_3], dans l'ordre. Exposées pour qu'un
         * test JVM vérifie leur contenu (aucune base SQLite n'est disponible
         * hors appareil). SQL pur, sans horloge ni aléa : deux exécutions sur la
         * même base donnent le même index.
         *
         * 1. Les deux colonnes de suivi du texte complet (lecture hors connexion).
         * 2. L'index plein texte est supprimé puis recréé avec le tokenizer
         *    unicode61. Supprimer la table virtuelle emporte ses tables
         *    annexes (`articles_fts_content`, `_segments`...).
         * 3. Réindexation depuis `articles`, sans toucher à la table `articles` :
         *    aucun article n'est perdu. Le corps indexé est `textContent`, comme
         *    à l'enregistrement d'un article (vide si absent).
         */
        val SQL_MIGRATION_2_3: List<String> = listOf(
            "ALTER TABLE `articles` ADD COLUMN `texteCompletEssaiLe` INTEGER",
            "ALTER TABLE `articles` ADD COLUMN `texteCompletEchec` INTEGER NOT NULL DEFAULT 0",
            "DROP TABLE IF EXISTS `articles_fts`",
            "CREATE VIRTUAL TABLE IF NOT EXISTS `articles_fts` USING FTS4(" +
                "`title` TEXT NOT NULL, `body` TEXT NOT NULL, tokenize=unicode61)",
            "INSERT INTO `articles_fts`(`rowid`, `title`, `body`) " +
                "SELECT `id`, `title`, COALESCE(`textContent`, '') FROM `articles` ORDER BY `id`",
        )

        /**
         * Recherche en français (diacritiques repliés) et suivi du texte complet.
         * Écrite à la main pour ne pas perdre les articles déjà collectés.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SQL_MIGRATION_2_3.forEach { db.execSQL(it) }
            }
        }

        /**
         * Aucun filet destructif : une montée de version sans Migration doit
         * échouer en développement, pas effacer abonnements et favoris chez
         * l'utilisateur. La prochaine version (4) devra fournir sa Migration.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rss.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
