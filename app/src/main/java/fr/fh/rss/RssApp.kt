package fr.fh.rss

import android.app.Application
import android.content.Context
import fr.fh.rss.data.FeedRepository
import fr.fh.rss.data.SettingsRepository
import fr.fh.rss.data.db.AppDatabase
import fr.fh.rss.data.net.Http
import kotlinx.coroutines.flow.first

class RssApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}

/** Injection simple : une seule instance de chaque brique, sans framework. */
class AppContainer(context: Context) {

    private val app: Context = context.applicationContext

    private val base by lazy { AppDatabase.build(app) }

    val settings: SettingsRepository by lazy { SettingsRepository(app) }

    private val http: Http by lazy {
        Http("RSSbyFabriceHeuvrard/${BuildConfig.VERSION_NAME} (Android)")
    }

    val repository: FeedRepository by lazy {
        FeedRepository(
            folderDao = base.folderDao(),
            feedDao = base.feedDao(),
            articleDao = base.articleDao(),
            http = http,
            archiveMax = { settings.flow.first().archiveMax },
        )
    }
}
