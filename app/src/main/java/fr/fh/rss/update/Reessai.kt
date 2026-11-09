package fr.fh.rss.update

/**
 * Politique de réessai des appels GitHub.
 *
 * Le CDN de GitHub répond par moments 502/503/504 sur les assets de release
 * (constaté le 09/11/2026 : une requête sur trois en 504 pour `app-release.apk`
 * et pour `version.json`, la même URL répondant 200 juste après). Sans réessai,
 * l'application annonçait « mise à jour impossible » alors que le fichier était
 * disponible une seconde plus tard.
 */
object Reessai {

    /** Nombre total d'essais pour un appel de mise à jour. */
    const val TENTATIVES = 3

    /** Une réponse 5xx est passagère : on retente. Un 404 ou un 403, non. */
    fun reessayable(code: Int): Boolean = code in 500..599

    /** Attente avant l'essai suivant, croissante (1 s, 2 s). */
    fun pauseMs(essai: Int): Long = 1_000L * essai.coerceAtLeast(1)

    /**
     * Libellé d'une réponse en échec. Les codes de surcharge sont explicités :
     * « HTTP 504 » seul laisse croire à une panne de l'application.
     */
    fun raison(code: Int): String = when (code) {
        502, 503, 504 ->
            "serveur GitHub momentanément indisponible (HTTP $code), réessayez dans un instant"
        408 -> "GitHub n'a pas répondu à temps (HTTP 408)"
        else -> "HTTP $code"
    }
}
