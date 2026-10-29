package fr.fh.rss.data

import java.security.MessageDigest

internal fun empreinte(texte: String): String {
    val octets = MessageDigest.getInstance("SHA-256").digest(texte.toByteArray())
    return octets.joinToString("") { "%02x".format(it) }.take(32)
}
