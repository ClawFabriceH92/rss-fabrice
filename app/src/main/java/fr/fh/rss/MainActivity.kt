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
import fr.fh.rss.ui.AppRoot
import fr.fh.rss.ui.MainViewModel
import fr.fh.rss.ui.theme.RssTheme
import fr.fh.rss.work.Notifications

class MainActivity : ComponentActivity() {

    private val lienEntrant = mutableStateOf<String?>(null)

    /** Android 13+ : sans cette permission, une notification reste invisible. */
    private val demandeNotification =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lienEntrant.value = lienDepuisIntent(intent)
        Notifications.creerCanal(this)
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
