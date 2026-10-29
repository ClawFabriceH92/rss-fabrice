package fr.fh.rss.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Client HTTP commun : requêtes conditionnelles, délais, User-Agent identifiable. */
class Http(private val userAgent: String) {

    data class Reponse(
        val code: Int,
        val body: String?,
        val etag: String?,
        val lastModified: String?,
        val urlFinale: String?,
    ) {
        val notModified: Boolean get() = code == 304
        val ok: Boolean get() = code in 200..299
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Throws(IOException::class)
    fun get(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
    ): Reponse {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/rss+xml, application/atom+xml, application/feed+json, application/xml, text/xml, text/html;q=0.9, */*;q=0.8")
        if (!etag.isNullOrBlank()) builder.header("If-None-Match", etag)
        if (!lastModified.isNullOrBlank()) builder.header("If-Modified-Since", lastModified)

        client.newCall(builder.build()).execute().use { reponse ->
            val corps = if (reponse.code == 304) null else reponse.body?.string()
            return Reponse(
                code = reponse.code,
                body = corps,
                etag = reponse.header("ETag"),
                lastModified = reponse.header("Last-Modified"),
                urlFinale = reponse.request.url.toString(),
            )
        }
    }
}
