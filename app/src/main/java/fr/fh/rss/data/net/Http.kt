package fr.fh.rss.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client HTTP commun : requêtes conditionnelles, délais, User-Agent identifiable.
 * Ouvert pour que les tests puissent simuler les réponses et les pannes réseau.
 */
open class Http(private val userAgent: String) {

    data class Reponse(
        val code: Int,
        val body: String?,
        val etag: String?,
        val lastModified: String?,
        val urlFinale: String?,
        /** Content-Type de la réponse, tel que reçu. */
        val contentType: String? = null,
        /** Vrai si l'adresse demandée a changé définitivement (301 ou 308 à chaque étape). */
        val redirectionPermanente: Boolean = false,
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

    /** Collecte d'un flux : corps plafonné à [CorpsHttp.MAX_FLUX]. */
    @Throws(IOException::class)
    open fun get(
        url: String,
        etag: String? = null,
        lastModified: String? = null,
    ): Reponse = executer(url, etag, lastModified, page = false)

    /**
     * Page lue par le lecteur : corps plafonné à [CorpsHttp.MAX_PAGE], et
     * refus d'un contenu qui n'est pas une page (PDF, audio, image) avant tout
     * téléchargement.
     */
    @Throws(IOException::class)
    open fun getPage(url: String): Reponse = executer(url, null, null, page = true)

    private fun executer(
        url: String,
        etag: String?,
        lastModified: String?,
        page: Boolean,
    ): Reponse {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/rss+xml, application/atom+xml, application/feed+json, application/xml, text/xml, text/html;q=0.9, */*;q=0.8")
        if (!etag.isNullOrBlank()) builder.header("If-None-Match", etag)
        if (!lastModified.isNullOrBlank()) builder.header("If-Modified-Since", lastModified)

        client.newCall(builder.build()).execute().use { reponse ->
            val typeRecu = reponse.header("Content-Type")
            val corps = when {
                reponse.code == 304 -> null
                // Un code d'erreur n'a pas besoin d'être lu en entier : l'appelant ne garde que le code.
                !reponse.isSuccessful -> null
                else -> {
                    if (page && !CorpsHttp.typePageAcceptable(typeRecu)) {
                        throw IOException("contenu non lisible (${typeRecu?.substringBefore(';')?.trim()})")
                    }
                    val flux = reponse.body
                    val max = if (page) CorpsHttp.MAX_PAGE else CorpsHttp.MAX_FLUX
                    flux?.let {
                        CorpsHttp.decoder(CorpsHttp.lire(it.byteStream(), max, it.contentLength()), typeRecu)
                    }
                }
            }
            return Reponse(
                code = reponse.code,
                body = corps,
                etag = reponse.header("ETag"),
                lastModified = reponse.header("Last-Modified"),
                urlFinale = reponse.request.url.toString(),
                contentType = typeRecu,
                redirectionPermanente = redirectionPermanente(reponse),
            )
        }
    }

    /** Remonte la chaîne de redirections suivies par OkHttp. */
    private fun redirectionPermanente(reponse: okhttp3.Response): Boolean {
        val codes = ArrayList<Int>()
        var precedente = reponse.priorResponse
        while (precedente != null) {
            codes += precedente.code
            precedente = precedente.priorResponse
        }
        return CorpsHttp.redirectionPermanente(codes)
    }
}
