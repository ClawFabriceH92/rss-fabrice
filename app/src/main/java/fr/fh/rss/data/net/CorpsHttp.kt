package fr.fh.rss.data.net

import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Règles de lecture d'une réponse HTTP, sans dépendance à OkHttp ni à Android :
 * plafond de taille, type de contenu accepté, encodage et redirections. Tout
 * se vérifie sur JVM.
 */
object CorpsHttp {

    /** Plafond d'un flux RSS, Atom ou JSON. */
    const val MAX_FLUX = 5 * 1024 * 1024

    /** Plafond d'une page lue par le lecteur. */
    const val MAX_PAGE = 3 * 1024 * 1024

    /**
     * Lit au plus [max] octets. Au-delà, la lecture s'arrête et une
     * [IOException] explicite remplace un OutOfMemoryError.
     *
     * [longueurAnnoncee] (en-tête Content-Length, -1 si inconnue) permet de
     * refuser sans rien télécharger.
     */
    @Throws(IOException::class)
    fun lire(flux: InputStream, max: Int, longueurAnnoncee: Long = -1L): ByteArray {
        if (longueurAnnoncee > max) throw IOException(messageTropGros(max))
        val sortie = java.io.ByteArrayOutputStream(minOf(max, 64 * 1024))
        val tampon = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val lus = flux.read(tampon)
            if (lus < 0) break
            total += lus
            if (total > max) throw IOException(messageTropGros(max))
            sortie.write(tampon, 0, lus)
        }
        return sortie.toByteArray()
    }

    private fun messageTropGros(max: Int) = "réponse trop volumineuse (plus de ${max / (1024 * 1024)} Mo)"

    /**
     * Vrai si le Content-Type est celui d'une page lisible. Un type absent est
     * toléré (serveur peu soigneux) ; un PDF, un audio ou une image ne le sont pas.
     */
    fun typePageAcceptable(contentType: String?): Boolean {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (type.isEmpty()) return true
        return type == "text/html" || type == "application/xhtml+xml" || type == "text/plain"
    }

    /** Une suite de redirections est permanente si chaque étape l'est (301 ou 308). */
    fun redirectionPermanente(codes: List<Int>): Boolean =
        codes.isNotEmpty() && codes.all { it == 301 || it == 308 }

    // ------------------------------------------------------------ encodage

    /**
     * Décode les octets d'une réponse. Ordre : charset du Content-Type, marque
     * d'ordre des octets (BOM), prologue `<?xml encoding="…"?>`, balise
     * `<meta charset>` d'une page HTML, puis UTF-8. Un flux ISO-8859-1 servi
     * sans charset garde ainsi ses accents.
     */
    fun decoder(octets: ByteArray, contentType: String? = null): String {
        val bom = bomDe(octets)
        val charset = charsetEntete(contentType)
            ?: bom?.first
            ?: charsetDeclare(octets)
            ?: StandardCharsets.UTF_8
        // Le BOM n'appartient pas au texte : on le saute s'il correspond au charset retenu.
        val saut = if (bom != null && bom.first == charset) bom.second else 0
        return String(octets, saut, octets.size - saut, charset)
    }

    private fun charsetEntete(contentType: String?): Charset? {
        val valeur = contentType ?: return null
        val brut = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9._:-]+)", RegexOption.IGNORE_CASE)
            .find(valeur)?.groupValues?.get(1) ?: return null
        return charsetSur(brut)
    }

    /** Charset et nombre d'octets du BOM, ou null. */
    private fun bomDe(o: ByteArray): Pair<Charset, Int>? = when {
        o.size >= 3 && o[0] == 0xEF.toByte() && o[1] == 0xBB.toByte() && o[2] == 0xBF.toByte() ->
            StandardCharsets.UTF_8 to 3
        o.size >= 2 && o[0] == 0xFE.toByte() && o[1] == 0xFF.toByte() -> StandardCharsets.UTF_16BE to 2
        o.size >= 2 && o[0] == 0xFF.toByte() && o[1] == 0xFE.toByte() -> StandardCharsets.UTF_16LE to 2
        else -> null
    }

    private val prologue = Regex(
        "^\\s*<\\?xml[^>]*?encoding\\s*=\\s*[\"']([A-Za-z0-9._:-]+)[\"']",
        RegexOption.IGNORE_CASE,
    )
    private val metaCharset = Regex(
        "<meta[^>]+charset\\s*=\\s*[\"']?([A-Za-z0-9._:-]+)",
        RegexOption.IGNORE_CASE,
    )

    private fun charsetDeclare(octets: ByteArray): Charset? {
        // L'en-tête est en ASCII dans tous les encodages courants : on le lit octet par octet.
        val debut = String(octets, 0, minOf(octets.size, 2048), StandardCharsets.ISO_8859_1)
        val nom = prologue.find(debut)?.groupValues?.get(1)
            ?: metaCharset.find(debut)?.groupValues?.get(1)
            ?: return null
        return charsetSur(nom)
    }

    private fun charsetSur(nom: String): Charset? =
        runCatching { Charset.forName(nom) }.getOrNull()
}
