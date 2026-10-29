package fr.fh.rss.data.net

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

/** Dates de flux : RFC 822 (RSS) et ISO 8601 (Atom, JSON Feed). */
object Dates {

    private val formatsRfc822 = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm zzz",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss zzz",
    )

    fun parse(raw: String?): Long? {
        val texte = raw?.trim().orEmpty()
        if (texte.isEmpty()) return null

        for (motif in formatsRfc822) {
            val date = runCatching {
                SimpleDateFormat(motif, Locale.ENGLISH).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                    isLenient = true
                }.parse(texte)
            }.getOrNull()
            if (date != null) return date.time
        }

        runCatching { return OffsetDateTime.parse(texte).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(texte).toEpochMilli() }
        runCatching {
            return LocalDateTime.parse(texte).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val sansSecondes = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }.parse(texte)
        }.getOrNull()
        return sansSecondes?.time
    }
}
