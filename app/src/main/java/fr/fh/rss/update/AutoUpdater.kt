package fr.fh.rss.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/** Les deux passages par le système dont le flux de mise à jour a besoin. */
object AutoUpdater {

    private const val TAG = "RssUpdate"

    fun install(context: Context, file: File): Result<Unit> = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure { Log.w(TAG, "installation impossible", it) }

    fun openInstallSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { Log.w(TAG, "réglages d'installation inaccessibles", it) }
    }
}
