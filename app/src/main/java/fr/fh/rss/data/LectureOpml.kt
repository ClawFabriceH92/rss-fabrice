package fr.fh.rss.data

import java.io.InputStream
import java.nio.charset.Charset

/**
 * Lecture du fichier OPML choisi par l'utilisateur, sans dépendance à Android.
 *
 * L'appelant exécute ceci hors du thread principal : le fichier vient du
 * sélecteur de documents (stockage, cloud) et peut être lent ou énorme.
 */
object LectureOpml {

    /** Au-delà, ce n'est pas un OPML de sources : on refuse plutôt que de saturer la mémoire. */
    const val TAILLE_MAX_OCTETS = 5 * 1024 * 1024

    private val ENCODAGE_PROLOGUE =
        Regex("^\\s*<\\?xml[^>]*encoding\\s*=\\s*[\"']([A-Za-z0-9._:-]+)[\"']", RegexOption.IGNORE_CASE)

    /**
     * Texte du fichier, ou `null` s'il est illisible : flux absent, erreur de
     * lecture, fichier vide ou plus gros que [TAILLE_MAX_OCTETS].
     */
    fun lire(flux: InputStream?, tailleMax: Int = TAILLE_MAX_OCTETS): String? {
        if (flux == null) return null
        val octets = try {
            flux.use { lireBorne(it, tailleMax) }
        } catch (_: java.io.IOException) {
            null
        } catch (_: SecurityException) {
            null
        } ?: return null
        if (octets.isEmpty()) return null
        val texte = decoder(octets)
        return texte.takeIf { it.isNotBlank() }
    }

    /** Lit au plus [tailleMax] octets ; `null` si le flux en contient davantage. */
    private fun lireBorne(flux: InputStream, tailleMax: Int): ByteArray? {
        val sortie = java.io.ByteArrayOutputStream()
        val tampon = ByteArray(8 * 1024)
        while (true) {
            val lus = flux.read(tampon)
            if (lus < 0) break
            if (sortie.size() + lus > tailleMax) return null
            sortie.write(tampon, 0, lus)
        }
        return sortie.toByteArray()
    }

    /** UTF-8 par défaut, sauf si le prologue XML déclare un autre encodage connu. */
    private fun decoder(octets: ByteArray): String {
        val debut = String(octets, 0, minOf(octets.size, 200), Charsets.ISO_8859_1)
        val declare = ENCODAGE_PROLOGUE.find(debut)?.groupValues?.get(1)
        val jeu = declare?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(octets, jeu).removePrefix("\uFEFF")
    }
}
