package fr.fh.rss.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, FeedEntity::class, ArticleEntity::class, ArticleFts::class],
    version = 2,
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
         * Aucun filet destructif : une montée de version sans Migration doit
         * échouer en développement, pas effacer abonnements et favoris chez
         * l'utilisateur. La prochaine version (3) devra fournir sa Migration.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rss.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
