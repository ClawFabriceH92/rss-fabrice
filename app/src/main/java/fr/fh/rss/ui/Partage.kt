package fr.fh.rss.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import fr.fh.rss.data.ExportsOpml
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Export OPML : fichier écrit dans files/exports puis feuille de partage Android. */
fun partagerOpml(contexte: Context, xml: String) {
    runCatching {
        val dossier = File(contexte.filesDir, "exports").apply { mkdirs() }
        val horodatage = SimpleDateFormat("yyyyMMdd-HHmm", Locale.FRANCE).format(Date())
        val fichier = File(dossier, ExportsOpml.nomFichier(horodatage))
        fichier.writeText(xml, Charsets.UTF_8)
        // Les anciens exports ne servent plus : ne garder que les plus récents.
        ExportsOpml.purger(dossier)

        val uri = FileProvider.getUriForFile(contexte, "${contexte.packageName}.fileprovider", fichier)
        val envoi = Intent(Intent.ACTION_SEND).apply {
            type = "text/xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sources RSS")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        contexte.startActivity(
            Intent.createChooser(envoi, "Exporter les sources").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
