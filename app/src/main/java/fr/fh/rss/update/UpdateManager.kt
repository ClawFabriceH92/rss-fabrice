package fr.fh.rss.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import fr.fh.rss.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Mise à jour pilotée depuis l'application.
 *
 * Le téléchargement passe par OkHttp et non par DownloadManager : GitHub remet
 * l'APK à un CDN par une chaîne de redirections que DownloadManager ne suit pas
 * toujours, ce qui produisait des fichiers tronqués que l'installeur refusait
 * (« problème lors de l'analyse du package »).
 */
object UpdateManager {

    private const val TAG = "RssUpdate"
    private const val DIR = "updates"

    sealed interface State {
        data object Idle : State
        data class Available(val version: String, val notes: String, val apkUrl: String) : State
        data class Downloading(val version: String, val progress: Float) : State
        data class ReadyToInstall(val version: String, val file: File) : State
        data class UpToDate(val version: String) : State
        data class Failed(val detail: String?) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var lastCheckAt = UpdateSchedule.NEVER

    fun checkAtLaunch(context: Context?) {
        if (BuildConfig.DEBUG) return
        check(context, manual = false)
    }

    fun checkNow(context: Context? = null) = check(context, manual = true)

    private fun check(context: Context?, manual: Boolean) {
        if (job?.isActive == true) return
        val maintenant = System.currentTimeMillis()
        if (!UpdateSchedule.isDue(manual, lastCheckAt, maintenant)) return
        lastCheckAt = maintenant
        job = scope.launch {
            when (val resultat = UpdateChecker.check()) {
                is UpdateChecker.CheckResult.Available -> {
                    val info = resultat.info
                    Log.i(TAG, "version ${info.version} disponible")
                    _state.value = State.Available(info.version, info.notes, info.apkUrl)
                    if (!manual && context != null) preparerEnSilence(context, info.version, info.apkUrl)
                }

                UpdateChecker.CheckResult.UpToDate -> {
                    _state.value = if (manual) State.UpToDate(BuildConfig.VERSION_NAME) else State.Idle
                }

                is UpdateChecker.CheckResult.Unreachable -> {
                    Log.i(TAG, UpdateChecker.logLine(resultat))
                    lastCheckAt = UpdateSchedule.NEVER
                    _state.value = if (manual) State.Failed(resultat.reason) else State.Idle
                }
            }
        }
    }

    /**
     * Téléchargement silencieux, uniquement sur un réseau non facturé :
     * tirer un APK de plusieurs dizaines de Mo sur les données mobiles sans
     * prévenir n'est pas un service.
     */
    private suspend fun preparerEnSilence(context: Context, version: String, url: String) {
        fichierValide(context, version)?.let {
            _state.value = State.ReadyToInstall(version, it)
            return
        }
        if (!reseauNonFacture(context)) {
            Log.i(TAG, "téléchargement différé (réseau facturé) v$version")
            return
        }
        _state.value = State.Downloading(version, 0f)
        _state.value = runCatching { fetch(context, version, url) }.fold(
            onSuccess = { State.ReadyToInstall(version, it) },
            onFailure = {
                Log.w(TAG, "téléchargement silencieux impossible", it)
                State.Available(version, "", url)
            },
        )
    }

    private fun reseauNonFacture(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val reseau = cm.activeNetwork ?: return false
        val capacites = cm.getNetworkCapabilities(reseau) ?: return false
        capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    private fun fichierValide(context: Context, version: String): File? {
        val fichier = File(File(context.filesDir, DIR), "rss-fabrice-v$version.apk")
        if (!fichier.exists() || fichier.length() == 0L) return null
        val entete = ByteArray(2)
        runCatching { fichier.inputStream().use { it.read(entete) } }.getOrNull()
        return if (String(entete) == "PK") fichier else null
    }

    fun download(context: Context) {
        val disponible = _state.value as? State.Available ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = State.Downloading(disponible.version, 0f)
            _state.value = runCatching {
                fetch(context, disponible.version, disponible.apkUrl)
            }.fold(
                onSuccess = { State.ReadyToInstall(disponible.version, it) },
                onFailure = {
                    Log.w(TAG, "téléchargement impossible", it)
                    State.Failed(it.message)
                },
            )
        }
    }

    private suspend fun fetch(context: Context, version: String, url: String): File =
        withContext(Dispatchers.IO) {
            val dossier = File(context.filesDir, DIR).apply { mkdirs() }
            dossier.listFiles()?.forEach { it.delete() }
            val cible = File(dossier, "rss-fabrice-v$version.apk")

            val requete = Request.Builder().url(url).get().build()
            http.newCall(requete).execute().use { reponse ->
                if (!reponse.isSuccessful) error("HTTP ${reponse.code}")
                val corps = reponse.body ?: error("Corps vide")
                val total = corps.contentLength()
                var ecrit = 0L
                corps.byteStream().use { entree ->
                    cible.outputStream().use { sortie ->
                        val tampon = ByteArray(64 * 1024)
                        var lu = entree.read(tampon)
                        while (lu != -1) {
                            sortie.write(tampon, 0, lu)
                            ecrit += lu
                            if (total > 0) {
                                _state.value = State.Downloading(
                                    version,
                                    (ecrit.toFloat() / total).coerceIn(0f, 1f),
                                )
                            }
                            lu = entree.read(tampon)
                        }
                    }
                }
            }
            if (cible.length() == 0L) error("Téléchargement vide")
            val entete = ByteArray(2)
            cible.inputStream().use { it.read(entete) }
            if (String(entete) != "PK") {
                cible.delete()
                error("fichier téléchargé invalide (ce n'est pas un APK)")
            }
            cible
        }

    /**
     * Installe l'APK téléchargé. Sans l'autorisation système
     * « installer des applications inconnues », l'appel est désormais visible :
     * l'état passe en échec avec la cause au lieu d'échouer en silence.
     */
    fun install(context: Context) {
        val pret = _state.value as? State.ReadyToInstall ?: return
        if (!canInstall(context)) {
            _state.value = State.Failed(
                "Android demande d'autoriser l'installation depuis cette application.",
            )
            return
        }
        AutoUpdater.install(context, pret.file).onFailure {
            _state.value = State.Failed("installation impossible : ${it.message.orEmpty()}")
        }
    }

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openInstallSettings(context: Context) = AutoUpdater.openInstallSettings(context)

    fun dismiss() {
        _state.value = State.Idle
    }
}
