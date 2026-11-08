package fr.fh.rss.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Test de connectivité avant une collecte : évite de lancer N requêtes vouées à l'échec. */
object Reseau {

    /**
     * Vrai si le réseau actif offre Internet et a été validé par Android.
     * En cas de doute (service indisponible, permission refusée), on laisse la
     * collecte tenter sa chance plutôt que de la bloquer à tort.
     */
    fun disponible(contexte: Context): Boolean = runCatching {
        val gestionnaire = contexte.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching true
        val capacites = gestionnaire.getNetworkCapabilities(gestionnaire.activeNetwork)
            ?: return@runCatching false
        capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capacites.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(true)
}
