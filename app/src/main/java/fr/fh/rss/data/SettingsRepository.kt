package fr.fh.rss.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
)

class SettingsRepository(private val context: Context) {

    private object Cles {
        val theme = stringPreferencesKey("theme")
        val vue = stringPreferencesKey("vue")
        val collecte = booleanPreferencesKey("collecte_periodique")
        val intervalle = intPreferencesKey("intervalle_minutes")
        val wifi = booleanPreferencesKey("wifi_uniquement")
        val archive = intPreferencesKey("archive_max")
    }

    val flow: Flow<Reglages> = context.dataStore.data.map { p ->
        Reglages(
            theme = p[Cles.theme] ?: "systeme",
            vue = p[Cles.vue] ?: "liste",
            collectePeriodique = p[Cles.collecte] ?: false,
            intervalleMinutes = p[Cles.intervalle] ?: 60,
            wifiUniquement = p[Cles.wifi] ?: true,
            archiveMax = p[Cles.archive] ?: 20000,
        )
    }

    suspend fun setTheme(valeur: String) = context.dataStore.edit { it[Cles.theme] = valeur }

    suspend fun setVue(valeur: String) = context.dataStore.edit { it[Cles.vue] = valeur }

    suspend fun setCollectePeriodique(actif: Boolean) =
        context.dataStore.edit { it[Cles.collecte] = actif }

    suspend fun setIntervalle(minutes: Int) =
        context.dataStore.edit { it[Cles.intervalle] = minutes.coerceIn(15, 1440) }

    suspend fun setWifiUniquement(actif: Boolean) = context.dataStore.edit { it[Cles.wifi] = actif }

    suspend fun setArchiveMax(nombre: Int) =
        context.dataStore.edit { it[Cles.archive] = nombre.coerceIn(500, 200000) }
}
