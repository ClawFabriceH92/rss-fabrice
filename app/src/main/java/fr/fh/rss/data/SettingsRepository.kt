package fr.fh.rss.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reglages")

/** Réglages hors base, comme prévu au cahier des charges (DataStore). */
data class Reglages(
    val theme: String = "systeme",        // systeme | clair | sombre
    val vue: String = "liste",            // liste | cartes | magazine
    val collectePeriodique: Boolean = false,
    val intervalleMinutes: Int = 60,      // 15 à 1440
    val wifiUniquement: Boolean = true,
    val archiveMax: Int = 20000,
    val ollamaUrl: String = fr.fh.rss.data.net.Ollama.ADRESSE_DEFAUT,
    val ollamaModele: String = fr.fh.rss.data.net.Ollama.MODELE_DEFAUT,
    /** Notification après une collecte qui rapporte des articles. */
    val notifierNouveaux: Boolean = true,
    /** Silence des notifications entre 22 h et 7 h. */
    val silenceNuit: Boolean = true,
    /** Taille du texte du lecteur en pour cent (A- / A+), voir [TailleTexte]. */
    val tailleTexte: Int = TailleTexte.DEFAUT,
)

class SettingsRepository(private val context: Context) {

    private object Cles {
        val theme = stringPreferencesKey("theme")
        val vue = stringPreferencesKey("vue")
        val collecte = booleanPreferencesKey("collecte_periodique")
        val intervalle = intPreferencesKey("intervalle_minutes")
        val wifi = booleanPreferencesKey("wifi_uniquement")
        val archive = intPreferencesKey("archive_max")
        val ollamaUrl = stringPreferencesKey("ollama_url")
        val ollamaModele = stringPreferencesKey("ollama_modele")
        val notifier = booleanPreferencesKey("notifier_nouveaux")
        val silence = booleanPreferencesKey("silence_nuit")
        val derniereNotif = longPreferencesKey("derniere_notif_at")
        val tailleTexte = intPreferencesKey("taille_texte_lecteur")
    }

    val flow: Flow<Reglages> = context.dataStore.data.map { p ->
        Reglages(
            theme = p[Cles.theme] ?: "systeme",
            vue = p[Cles.vue] ?: "liste",
            collectePeriodique = p[Cles.collecte] ?: false,
            intervalleMinutes = p[Cles.intervalle] ?: 60,
            wifiUniquement = p[Cles.wifi] ?: true,
            archiveMax = p[Cles.archive] ?: 20000,
            ollamaUrl = p[Cles.ollamaUrl] ?: fr.fh.rss.data.net.Ollama.ADRESSE_DEFAUT,
            ollamaModele = p[Cles.ollamaModele] ?: fr.fh.rss.data.net.Ollama.MODELE_DEFAUT,
            notifierNouveaux = p[Cles.notifier] ?: true,
            silenceNuit = p[Cles.silence] ?: true,
            tailleTexte = TailleTexte.normaliser(p[Cles.tailleTexte] ?: TailleTexte.DEFAUT),
        )
    }

    /**
     * Date de la dernière notification (ou du dernier passage de l'utilisateur
     * dans l'application). Hors base : aucune migration Room. 0 = jamais posée.
     */
    suspend fun derniereNotifAt(): Long = context.dataStore.data.first()[Cles.derniereNotif] ?: 0L

    suspend fun setDerniereNotifAt(valeur: Long) =
        context.dataStore.edit { it[Cles.derniereNotif] = valeur }

    suspend fun setOllamaUrl(valeur: String) =
        context.dataStore.edit { it[Cles.ollamaUrl] = fr.fh.rss.data.net.Ollama.normaliser(valeur) }

    suspend fun setOllamaModele(valeur: String) =
        context.dataStore.edit { it[Cles.ollamaModele] = valeur.trim() }

    suspend fun setTheme(valeur: String) = context.dataStore.edit { it[Cles.theme] = valeur }

    suspend fun setVue(valeur: String) = context.dataStore.edit { it[Cles.vue] = valeur }

    suspend fun setCollectePeriodique(actif: Boolean) =
        context.dataStore.edit { it[Cles.collecte] = actif }

    suspend fun setIntervalle(minutes: Int) =
        context.dataStore.edit { it[Cles.intervalle] = minutes.coerceIn(15, 1440) }

    suspend fun setWifiUniquement(actif: Boolean) = context.dataStore.edit { it[Cles.wifi] = actif }

    suspend fun setArchiveMax(nombre: Int) =
        context.dataStore.edit { it[Cles.archive] = nombre.coerceIn(500, 200000) }

    suspend fun setNotifierNouveaux(actif: Boolean) =
        context.dataStore.edit { it[Cles.notifier] = actif }

    suspend fun setSilenceNuit(actif: Boolean) =
        context.dataStore.edit { it[Cles.silence] = actif }

    suspend fun setTailleTexte(pourcent: Int) =
        context.dataStore.edit { it[Cles.tailleTexte] = TailleTexte.normaliser(pourcent) }
}
