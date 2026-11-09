package fr.fh.rss.data.net

/**
 * Propositions de sources suggérées par le modèle local.
 *
 * Un modèle de langage ne connaît pas les adresses de flux : il en invente une
 * bonne partie (et cite souvent des sites qui n'ont jamais eu de RSS). Rien
 * n'est donc proposé sans avoir été réellement demandé au réseau : une adresse
 * n'est retenue que si elle répond du XML avec au moins un article. Les adresses
 * déjà suivies ne sont jamais reproposées.
 */
object SuggestionsSources {

    /** Nombre de propositions demandées au modèle, et plafond d'adresses testées. */
    const val NOMBRE_DEMANDE = 12

    /** Plafond d'adresses vérifiées par le réseau (un modèle bavard peut en citer trente). */
    const val MAX_A_TESTER = 16

    enum class Etat { PROPOSE, DEJA_SUIVIE, SANS_FLUX }

    data class Proposition(
        val nom: String,
        val url: String,
        val etat: Etat,
        val articles: Int = 0,
        val detail: String = "",
    ) {
        val retenue: Boolean get() = etat == Etat.PROPOSE
    }

    /**
     * Consigne envoyée au modèle.
     *
     * On demande l'adresse du SITE, pas celle du flux : un modèle invente très
     * souvent une fausse adresse de flux (« /flux/rss/actualites.xml », mesuré :
     * 0 adresse valide sur 13 avec ministral-3:8b), alors qu'il connaît mieux
     * les domaines. L'application découvre ensuite le flux de la page.
     */
    fun consigne(nombre: Int = NOMBRE_DEMANDE): String =
        "Tu proposes des sources d'information à ajouter à un lecteur de flux. " +
            "Réponds uniquement par un tableau JSON, sans texte autour, au format " +
            "[{\"nom\": \"Nom du site\", \"site\": \"https://exemple.fr\"}]. " +
            "Donne $nombre sites complémentaires de ceux fournis, jamais un site déjà cité. " +
            "Indique l'adresse d'accueil du site (pas celle d'un flux), et varie les thèmes."

    /**
     * Contenu envoyé au modèle : les sources déjà suivies, une par ligne
     * (titre + adresse). Le modèle en déduit les thèmes couverts.
     */
    fun contenu(existantes: List<Pair<String, String>>): String = buildString {
        appendLine("Sources déjà suivies par l'utilisateur :")
        existantes.take(200).forEach { (titre, url) ->
            appendLine("- ${titre.ifBlank { url }} <$url>")
        }
        appendLine()
        append("Propose uniquement des sources absentes de cette liste.")
    }

    /** Clé de comparaison d'une adresse : schéma et casse ignorés, barre finale retirée. */
    fun cle(url: String): String {
        val net = url.trim()
        val sansSchema = net.removePrefix("https://").removePrefix("http://")
        return sansSchema.lowercase().trimEnd('/')
    }

    private val MOTIF_URL = Regex("""https?://[^\s"'<>()\[\]{}\\|^`]+""", RegexOption.IGNORE_CASE)

    /**
     * Adresses citées par le modèle, dans l'ordre du texte, dédoublonnées.
     *
     * Le format JSON est lu quand il est là ; sinon chaque adresse trouvée est
     * retenue avec, pour nom, le libellé qui la précède sur la même ligne (un
     * petit modèle rend souvent une liste à puces malgré la consigne).
     */
    fun extraire(texte: String): List<Pair<String, String>> {
        // clé de comparaison -> (nom, adresse telle que citée)
        val sortie = LinkedHashMap<String, Pair<String, String>>()

        for (objet in objetsJson(texte)) {
            val url = nettoyerUrl(objet.second) ?: continue
            val nom = objet.first.trim().ifBlank { hote(url) }
            sortie.putIfAbsent(cle(url), nom to url)
        }
        if (sortie.isNotEmpty()) return sortie.values.toList()

        for (ligne in texte.lineSequence()) {
            for (m in MOTIF_URL.findAll(ligne)) {
                val url = nettoyerUrl(m.value) ?: continue
                val avant = ligne.substring(0, m.range.first)
                    .trim()
                    .trim('-', '*', '•', '|', ':', '—', '·', ' ')
                    .trim('"', '\'', '(', ')')
                val nom = avant.substringAfterLast(':').trim().ifBlank { avant }.ifBlank { hote(url) }
                sortie.putIfAbsent(cle(url), nom to url)
            }
        }
        return sortie.values.toList()
    }

    /** Paires (nom, url) d'un tableau JSON, quand le modèle a répondu du JSON. */
    private fun objetsJson(texte: String): List<Pair<String, String>> {
        val debut = texte.indexOf('[')
        val fin = texte.lastIndexOf(']')
        if (debut < 0 || fin <= debut) return emptyList()
        val brut = texte.substring(debut, fin + 1)
        val resultat = mutableListOf<Pair<String, String>>()
        for (bloc in brut.split('}')) {
            val url = Regex(""""(?:url|xmlUrl|site|flux|adresse)"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(bloc)?.groupValues?.get(1)
                ?: MOTIF_URL.find(bloc)?.value
                ?: continue
            val nom = Regex(""""(?:nom|name|titre|title)"\s*:\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(bloc)?.groupValues?.get(1)
                .orEmpty()
            resultat += nom to url
        }
        return resultat
    }

    private fun nettoyerUrl(brut: String): String? {
        val url = brut.trim().trimEnd('.', ',', ';', ':', ')', ']', '}', '\'', '"', '>')
        val http = url.startsWith("http://") || url.startsWith("https://")
        return url.takeIf { http && it.length > 12 && it.contains('.') }
    }

    private fun hote(url: String): String =
        url.removePrefix("https://").removePrefix("http://").substringBefore('/').removePrefix("www.")

    /**
     * Adresses à tester : ni déjà suivies, ni en double, dans l'ordre du modèle,
     * plafonnées. Les [dejaSuivies] sont rendues à part pour être expliquées.
     */
    fun aTester(
        candidates: List<Pair<String, String>>,
        suivies: List<String>,
        maximum: Int = MAX_A_TESTER,
    ): List<Pair<String, String>> {
        val connues = suivies.map(::cle).toHashSet()
        val vues = HashSet<String>()
        return candidates.asSequence()
            .filter { (_, url) -> cle(url) !in connues }
            .filter { (_, url) -> vues.add(cle(url)) }
            .take(maximum)
            .toList()
    }

    /** Libellé de bilan affiché après une demande. */
    fun resume(propositions: List<Proposition>): String {
        val retenues = propositions.count { it.retenue }
        val sansFlux = propositions.count { it.etat == Etat.SANS_FLUX }
        return when {
            propositions.isEmpty() -> "Le modèle n'a proposé aucune adresse exploitable."
            retenues == 0 -> "Aucune des ${propositions.size} adresses proposées ne répond un flux."
            sansFlux == 0 -> "$retenues source(s) vérifiée(s), prêtes à ajouter."
            else -> "$retenues source(s) vérifiée(s) ; $sansFlux adresse(s) sans flux écartée(s)."
        }
    }
}
