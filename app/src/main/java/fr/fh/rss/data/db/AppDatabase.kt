package fr.fh.rss.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, FeedEntity::class, ArticleEntity::class, ArticleFts::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun feedDao(): FeedDao
    abstract fun articleDao(): ArticleDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rss.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
