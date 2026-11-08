package fr.fh.rss.data

import kotlin.math.roundToInt

/**
 * Taille du texte du lecteur, sans dépendance à Android pour rester testable sur JVM.
 *
 * Deux facteurs se combinent : le réglage A- / A+ de l'application (en pour cent)
 * et l'échelle de police du système. Le WebView ne suit pas toujours cette
 * dernière de lui-même : on la lui applique donc explicitement (textZoom).
 */
object TailleTexte {

    /** Réglage neutre : taille d'origine du lecteur. */
    const val DEFAUT = 100

    const val MIN = 70
    const val MAX = 200

    /** Pas d'un appui sur A- ou A+. */
    const val PAS = 10

    /** Bornes du zoom envoyé au WebView, quelles que soient les entrées. */
    private const val ZOOM_MIN = 50
    private const val ZOOM_MAX = 400

    /** Ramène une valeur lue (réglage ancien, corrompu) dans la plage permise. */
    fun normaliser(pourcent: Int): Int = pourcent.coerceIn(MIN, MAX)

    fun plus(pourcent: Int): Int = normaliser(normaliser(pourcent) + PAS)

    fun moins(pourcent: Int): Int = normaliser(normaliser(pourcent) - PAS)

    fun peutAugmenter(pourcent: Int): Boolean = normaliser(pourcent) < MAX

    fun peutDiminuer(pourcent: Int): Boolean = normaliser(pourcent) > MIN

    /**
     * Zoom du WebView : réglage de l'application multiplié par l'échelle de
     * police du système (1,0 = normale). Une échelle absurde (0, négative,
     * NaN) est ignorée.
     */
    fun zoomWebView(pourcent: Int, echellePolice: Float): Int {
        val echelle = if (echellePolice.isNaN() || echellePolice <= 0f) 1f else echellePolice
        return (normaliser(pourcent) * echelle).roundToInt().coerceIn(ZOOM_MIN, ZOOM_MAX)
    }
}
