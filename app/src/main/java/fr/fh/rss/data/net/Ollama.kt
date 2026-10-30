package fr.fh.rss.data.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client minimal pour un serveur Ollama local (le PC de Fabrice).
 *
 * Aucun appel ne sort du réseau domestique : le texte des articles n'est
 * envoyé qu'à l'adresse configurée dans les réglages.
 */
class Ollama {

    companion object {
        const val ADRESSE_DEFAUT = "http://192.168.0.162:11434"
        const val MODELE_DEFAUT = "ministral-3:8b"

        /** Consigne par défaut du résumé d'article. */
        const val CONSIGNE_RESUME =
            "Résume cet article en français en trois phrases maximum, sans introduction, " +
                "sans puces et sans formule de politesse. Garde les chiffres et les dates."

        /** Consigne par défaut de la synthèse des articles non lus. */
        const val CONSIGNE_SYNTHESE =
            "Voici les titres d'articles de presse non lus. Dégage en français les cinq sujets " +
                "principaux, chacun sur une ligne commençant par un tiret, en citant les thèmes " +
                "communs. Pas d'introduction ni de conclusion."

        fun normaliser(base: String): String {
            val propre = base.trim().ifEmpty { ADRESSE_DEFAUT }
            return propre.trimEnd('/')
        }
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    /** Modèles installés sur le serveur, dans l'ordre renvoyé par Ollama. */
    fun modeles(base: String): List<String> {
        val url = normaliser(base) + "/api/tags"
        val requete = Request.Builder().url(url).get().build()
        client.newCall(requete).execute().use { reponse ->
            val corps = reponse.body?.string()
            if (!reponse.isSuccessful) {
                throw IOException("Ollama a répondu ${reponse.code}")
            }
            if (corps.isNullOrBlank()) throw IOException("réponse vide")
            val modèles = JSONObject(corps).optJSONArray("models") ?: return emptyList()
            return buildList {
                for (i in 0 until modèles.length()) {
                    val nom = modèles.optJSONObject(i)?.optString("name").orEmpty()
                    if (nom.isNotBlank()) add(nom)
                }
            }
        }
    }

    /**
     * Envoie une consigne et un contenu, renvoie le texte du modèle.
     *
     * @throws IOException si le serveur ne répond pas ou renvoie une erreur.
     */
    fun demander(
        base: String,
        modele: String,
        consigne: String,
        contenu: String,
        nombreTokens: Int = 500,
    ): String {
        if (contenu.isBlank()) throw IOException("rien à analyser")
        val charge = JSONObject().apply {
            put("model", modele.ifBlank { MODELE_DEFAUT })
            put(
                "messages",
                org.json.JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", consigne))
                    put(JSONObject().put("role", "user").put("content", contenu.take(24000)))
                },
            )
            put("stream", false)
            put("options", JSONObject().put("temperature", 0.2).put("num_predict", nombreTokens))
        }
        val requete = Request.Builder()
            .url(normaliser(base) + "/api/chat")
            .post(charge.toString().toRequestBody(json))
            .build()
        client.newCall(requete).execute().use { reponse ->
            val corps = reponse.body?.string()
            if (!reponse.isSuccessful) {
                throw IOException("Ollama a répondu ${reponse.code}")
            }
            if (corps.isNullOrBlank()) throw IOException("réponse vide")
            val texte = JSONObject(corps)
                .optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .trim()
            if (texte.isEmpty()) throw IOException("réponse sans texte")
            return texte
        }
    }
}
