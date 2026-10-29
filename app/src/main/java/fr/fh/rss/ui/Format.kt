package fr.fh.rss.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dates relatives pour la liste, dates complètes pour le lecteur. */
fun dateCourte(millis: Long): String {
    if (millis <= 0) return ""
    val ecart = System.currentTimeMillis() - millis
    return when {
        ecart < 60_000 -> "à l'instant"
        ecart < 3_600_000 -> "il y a ${ecart / 60_000} min"
        ecart < 86_400_000 -> "il y a ${ecart / 3_600_000} h"
        ecart < 7 * 86_400_000L -> "il y a ${ecart / 86_400_000} j"
        else -> SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(millis))
    }
}

fun dateLongue(millis: Long): String =
    if (millis <= 0) "" else SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE).format(Date(millis))
