package fr.fh.rss.data.net

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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

        /**
         * Adresse utilisable par OkHttp : espaces retirés, http:// ajouté quand le
         * schéma manque (« 192.168.0.162:11434 »), barre finale retirée. Vide : adresse par défaut.
         */
        fun normaliser(base: String): String {
            val propre = base.trim().ifEmpty { ADRESSE_DEFAUT }
            val avecSchema = if (propre.contains("://")) propre else "http://$propre"
            return avecSchema.trimEnd('/')
        }

        /** Vrai si l'adresse normalisée est une adresse http(s) que OkHttp accepte. */
        fun adresseValide(base: String): Boolean = normaliser(base).toHttpUrlOrNull() != null

        /**
         * Message actionnable pour une erreur d'appel à Ollama, à la place de la
         * trace brute d'OkHttp (« failed to connect to /192.168.0.162… »).
         */
        fun messageErreur(base: String, erreur: Throwable): String {
            val adresse = normaliser(base)
            val hote = adresse.toHttpUrlOrNull()?.host
            return when {
                erreur is IllegalArgumentException || hote == null ->
                    "Adresse Ollama invalide : « ${base.trim()} ». Exemple : 192.168.0.162:11434"
                erreur is java.net.UnknownHostException ||
                    erreur is java.net.ConnectException ||
                    erreur is java.net.NoRouteToHostException ->
                    "Ollama injoignable sur $hote : es-tu sur le Wi-Fi de la maison ?"
                erreur is java.net.SocketTimeoutException && erreur.message.orEmpty().contains("connect", ignoreCase = true) ->
                    "Ollama injoignable sur $hote : es-tu sur le Wi-Fi de la maison ?"
                erreur is java.io.InterruptedIOException || erreur is java.net.SocketTimeoutException ->
                    "Ollama met trop de temps à répondre sur $hote : le modèle charge peut-être, réessaie dans un instant."
                else -> erreur.message?.takeIf { it.isNotBlank() } ?: "erreur inconnue (${erreur.javaClass.simpleName})"
            }
        }

        /** Vrai si le texte d'état affiché est une erreur (tout sauf une connexion réussie). */
        fun estEtatErreur(etat: String): Boolean =
            !(etat.startsWith("Connecté") || etat.startsWith("Serveur joignable"))
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    /** Modèles installés sur le serveur, dans l'ordre renvoyé par Ollama. */
    suspend fun modeles(base: String): List<String> {
        val url = normaliser(base) + "/api/tags"
        val requete = Request.Builder().url(url).get().build()
        return executer(requete) { reponse ->
            val corps = reponse.body?.string()
            if (!reponse.isSuccessful) {
                throw IOException("Ollama a répondu ${reponse.code}")
            }
            if (corps.isNullOrBlank()) throw IOException("réponse vide")
            val modèles = JSONObject(corps).optJSONArray("models")
            buildList {
                for (i in 0 until (modèles?.length() ?: 0)) {
                    val nom = modèles?.optJSONObject(i)?.optString("name").orEmpty()
                    if (nom.isNotBlank()) add(nom)
                }
            }
        }
    }

    /**
     * Exécute la requête sans bloquer : l'annulation de la coroutine annule
     * l'appel réseau (bouton « Annuler » d'un résumé).
     */
    private suspend fun <T> executer(requete: Request, lire: (Response) -> T): T =
        suspendCancellableCoroutine { suite ->
            val appel = client.newCall(requete)
            suite.invokeOnCancellation { appel.cancel() }
            appel.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (suite.isActive) suite.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val resultat = runCatching { response.use(lire) }
                    if (suite.isActive) suite.resumeWith(resultat)
                }
            })
        }

    /**
     * Envoie une consigne et un contenu, renvoie le texte du modèle.
     *
     * @throws IOException si le serveur ne répond pas ou renvoie une erreur.
     */
    suspend fun demander(
        base: String,
        modele: String,
        consigne: String,
        contenu: String,
        nombreTokens: Int = 500,
        /**
         * Coupe le raisonnement étendu des modèles qui le pratiquent (qwen3.8…) :
         * sans cela ils consomment tout le quota de tokens en réflexion et
         * renvoient un contenu vide (constaté le 09/11/2026, `done_reason=length`).
         * Les serveurs qui ne connaissent pas ce champ l'ignorent.
         */
        reflexion: Boolean = false,
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
            put("think", reflexion)
            put("options", JSONObject().put("temperature", 0.2).put("num_predict", nombreTokens))
        }
        val requete = Request.Builder()
            .url(normaliser(base) + "/api/chat")
            .post(charge.toString().toRequestBody(json))
            .build()
        return executer(requete) { reponse ->
            val corps = reponse.body?.string()
            if (reponse.code == 404) {
                throw IOException("modèle « ${modele.ifBlank { MODELE_DEFAUT }} » introuvable sur le serveur Ollama")
            }
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
            texte
        }
    }
}
