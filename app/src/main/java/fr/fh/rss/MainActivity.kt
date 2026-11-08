package fr.fh.rss

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import fr.fh.rss.data.ExportsOpml
import fr.fh.rss.ui.AppRoot
import fr.fh.rss.ui.MainViewModel
import fr.fh.rss.ui.theme.RssTheme
import fr.fh.rss.update.UpdateManager
import fr.fh.rss.work.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val lienEntrant = mutableStateOf<String?>(null)

    /** Android 13+ : sans cette permission, une notification reste invisible. */
    private val demandeNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Intent d'origine relu seulement au premier lancement : après une rotation,
        // il serait rejoué (nouvelle tentative d'abonnement au lien partagé).
        if (savedInstanceState == null) {
            lienEntrant.value = lienDepuisIntent(intent)
        }
        Notifications.creerCanal(this)
        // Purge des anciens exports OPML, hors du thread principal.
        val dossierExports = java.io.File(filesDir, "exports")
        lifecycleScope.launch(Dispatchers.IO) { runCatching { ExportsOpml.purger(dossierExports) } }
        // Contrôle de mise à jour au lancement (le plancher horaire reste géré par le manager).
        UpdateManager.checkAtLaunch(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Notifications.permissionAccordee(this)
        ) {
            demandeNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val vm: MainViewModel = viewModel()
            val reglages by vm.reglages.collectAsState()
            RssTheme(reglages.theme) {
                AppRoot(
                    vm = vm,
                    lienExterne = lienEntrant.value,
                    onLienUtilise = { lienEntrant.value = null },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        marquerVu()
    }

    override fun onStop() {
        super.onStop()
        // Ce qui est arrivé pendant que l'application était ouverte a été vu.
        marquerVu()
    }

    /** Retire la notification et avance le repère : les articles reçus jusqu'ici sont vus. */
    private fun marquerVu() {
        Notifications.annuler(this)
        val parametres = (application as RssApp).container.settings
        // Portée propre : à onStop, celle de l'activité peut disparaître avant l'écriture.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { parametres.setDerniereNotifAt(System.currentTimeMillis()) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lienDepuisIntent(intent)?.let { lienEntrant.value = it }
    }

    /** Partage depuis le navigateur (texte) ou ouverture directe d'une adresse. */
    private fun lienDepuisIntent(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { extraireUrl(it) }
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
    }

    private fun extraireUrl(texte: String): String {
        val trouve = Regex("https?://\\S+").find(texte)?.value
        return (trouve ?: texte).trim()
    }
}
