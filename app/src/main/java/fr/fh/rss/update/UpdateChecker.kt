package fr.fh.rss.update

import fr.fh.rss.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Vérifie les releases GitHub, comme notes-de-frais : la release roulante
 * `latest` porte version.json à côté de l'APK, et le manifeste est lu avant
 * l'API parce qu'un appel API non authentifié est plafonné à 60 par heure.
 *
 * Le dépôt doit rester PUBLIC : un dépôt privé répond 404 sans jeton sur les
 * assets, et l'application n'en a pas.
 */
object UpdateChecker {

    const val REPO = "ClawFabriceH92/rss-fabrice"

    const val MANIFEST_URL = "https://github.com/$REPO/releases/download/latest/version.json"
    const val API_URL = "https://api.github.com/repos/$REPO/releases"
    const val APK_URL = "https://github.com/$REPO/releases/download/latest/app-release.apk"

    private const val TIMEOUT_MS = 10_000

    data class UpdateInfo(
        val version: String,
        val apkUrl: String,
        val notes: String = "",
    )

    sealed interface CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult
        data object UpToDate : CheckResult
        data class Unreachable(val reason: String, val answered: Boolean = false) : CheckResult
    }

    class HttpStatusException(val code: Int, reason: String) : IOException(reason)

    suspend fun check(installed: String = BuildConfig.VERSION_NAME): CheckResult =
        withContext(Dispatchers.IO) {
            val manifeste = runCatching { fetch(MANIFEST_URL, github = false) }
            manifeste.getOrNull()
                ?.let { parseManifest(it, installed) }
                ?.let { return@withContext it }

            val api = runCatching { fetch(API_URL, github = true) }
            api.getOrNull()
                ?.let { parseApi(it, installed) }
                ?.let { return@withContext it }

            verdictEchec(manifeste, api)
        }

    fun logLine(result: CheckResult.Unreachable): String =
        if (result.answered) "GitHub a répondu : ${result.reason}"
        else "vérification impossible : ${result.reason}"

    internal fun verdictEchec(
        manifeste: Result<String>,
        api: Result<String>,
    ): CheckResult.Unreachable {
        val echecs = listOfNotNull(manifeste.exceptionOrNull(), api.exceptionOrNull())
        val reseau = echecs.firstOrNull { it !is HttpStatusException }
        val cause = reseau ?: api.exceptionOrNull() ?: manifeste.exceptionOrNull()
        val aRepondu = cause == null || cause is HttpStatusException
        return CheckResult.Unreachable(cause?.let { describe(it) } ?: "réponse illisible", aRepondu)
    }

    private fun fetch(url: String, github: Boolean): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "RSSbyFabriceHeuvrard/${BuildConfig.VERSION_NAME}")
            if (github) conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw HttpStatusException(
                    code,
                    httpReason(code, conn.getHeaderField("x-ratelimit-remaining")),
                )
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    internal fun httpReason(code: Int, rateLimitRemaining: String?): String = when {
        code == HttpURLConnection.HTTP_FORBIDDEN && rateLimitRemaining == "0" ->
            "quota GitHub épuisé pour cette adresse IP"
        code == HttpURLConnection.HTTP_FORBIDDEN -> "accès refusé par GitHub (HTTP 403)"
        code == HttpURLConnection.HTTP_NOT_FOUND -> "ressource absente (HTTP 404)"
        else -> "HTTP $code"
    }

    internal fun describe(e: Throwable): String = when (e) {
        is UnknownHostException -> "nom d'hôte introuvable, DNS injoignable"
        is SocketTimeoutException -> "délai d'attente dépassé"
        is NoRouteToHostException -> "aucune route vers l'hôte"
        is ConnectException -> "connexion impossible"
        is SSLException -> "échec TLS (${e.message.orEmpty()})"
        is IOException -> e.message.orEmpty().ifBlank { "erreur réseau" }
        else -> "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
    }

    internal fun parseManifest(body: String, installed: String): CheckResult? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val version = extractVersion(json.optString("version", "")) ?: return null
        val apkUrl = json.optString("apk", "").ifBlank { APK_URL }
        return if (compareVersions(version, installed) > 0) {
            CheckResult.Available(UpdateInfo(version, apkUrl, json.optString("notes", "")))
        } else {
            CheckResult.UpToDate
        }
    }

    internal fun parseApi(body: String, installed: String): CheckResult? {
        val releases = runCatching { JSONArray(body) }.getOrNull() ?: return null
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft")) continue
            val assets = release.optJSONArray("assets") ?: JSONArray()
            // La release roulante est étiquetée « latest » : la version se lit
            // alors dans le nom de l'APK publié.
            val version = extractVersion(release.optString("tag_name", ""))
                ?: extractVersion(release.optString("name", ""))
                ?: versionDepuisAssets(assets)
                ?: continue
            val apkUrl = pickApk(assets, version) ?: continue
            if (compareVersions(version, installed) > 0) {
                return CheckResult.Available(
                    UpdateInfo(version, apkUrl, release.optString("body", "")),
                )
            }
        }
        return CheckResult.UpToDate
    }

    private fun versionDepuisAssets(assets: JSONArray): String? =
        (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it)?.optString("name", "") }
            .filter { it.endsWith(".apk", ignoreCase = true) }
            .mapNotNull { extractVersion(it) }
            .maxByOrNull { version ->
                version.split(".").fold(0L) { acc, segmente ->
                    acc * 1000 + (segmente.toIntOrNull() ?: 0)
                }
            }

    fun extractVersion(text: String): String? =
        Regex("""\d+(?:\.\d+)+""").find(text)?.value

    fun pickApk(assets: JSONArray, version: String): String? {
        val apks = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .filter { it.optString("name", "").endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null
        val parVersion = apks.firstOrNull { it.optString("name").contains(version) }
        val canonique = apks.firstOrNull { it.optString("name") == "app-release.apk" }
        return (parVersion ?: canonique ?: apks.first())
            .optString("browser_download_url").ifBlank { null }
    }

    fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va > vb) return 1
            if (va < vb) return -1
        }
        return 0
    }
}
