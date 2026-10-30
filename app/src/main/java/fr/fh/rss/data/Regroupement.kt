package fr.fh.rss.data

/**
 * Regroupement des articles pour l'onglet IA : dépêches identiques publiées par
 * plusieurs sources, puis classement par thème.
 *
 * Tout est déterministe et testable sur JVM : aucune dépendance à Android ni à
 * un modèle de langage. Le modèle ne sert qu'aux résumés.
 */
object Regroupement {

    /** Un article retenu, avec les sources qui ont publié la même dépêche. */
    data class Groupe(
        val article: fr.fh.rss.data.db.ArticleEntity,
        val sources: List<String>,
    ) {
        val nombreSources: Int get() = sources.size
        val doublon: Boolean get() = sources.size > 1
    }

    private val PREFIXES = Regex(
        "^(direct|video|live|info|urgent|communique|depeche|afp|reuters|de presse)\\s*:?\\s*",
    )

    private val MENTIONS_AGENCE = Regex("\\((afp|reuters|ap|dpa|belga)\\)")

    /** Titre ramené à ses mots significatifs, pour comparer deux dépêches. */
    fun normaliserTitre(titre: String): String {
        val sansAccents = java.text.Normalizer
            .normalize(titre.replace('\u00a0', ' '), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        var texte = MENTIONS_AGENCE.replace(sansAccents.lowercase(), " ").trim()
        // Les préfixes de type « DIRECT : » ou « Info : » peuvent s'empiler.
        while (true) {
            val avant = texte
            texte = PREFIXES.replace(texte, "").trim()
            if (texte == avant) break
        }
        return texte.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }

    /**
     * Regroupe les articles dont le titre normalisé est identique.
     *
     * L'ordre d'entrée est conservé (l'appelant passe la liste déjà triée par
     * date décroissante) : le premier article du groupe est le plus récent.
     */
    fun regrouper(
        articles: List<fr.fh.rss.data.db.ArticleEntity>,
        titresSources: Map<Long, String>,
    ): List<Groupe> {
        val parTitre = LinkedHashMap<String, MutableList<fr.fh.rss.data.db.ArticleEntity>>()
        for (article in articles) {
            val cle = normaliserTitre(article.title)
            if (cle.isEmpty()) {
                parTitre["id:${article.id}"] = mutableListOf(article)
            } else {
                parTitre.getOrPut(cle) { mutableListOf() }.add(article)
            }
        }
        return parTitre.values.map { meme ->
            val premier = meme.first()
            val sources = meme.mapNotNull { titresSources[it.feedId] }.distinct()
            Groupe(premier, if (sources.isEmpty()) listOf("source inconnue") else sources)
        }
    }

    /** Nombre de dépêches en double repérées dans le lot. */
    fun nombreDoublons(groupes: List<Groupe>): Int = groupes.count { it.doublon }

    // ------------------------------------------------------------- doublons

    /**
     * Un titre plus court que ça est trop générique pour servir de clé : deux
     * « Édito » de deux journaux différents ne sont pas le même article.
     */
    const val LONGUEUR_TITRE_CLE = 20

    /** Paramètres d'URL qui ne changent pas l'article visé (traçage). */
    private val PARAMETRES_IGNORES = setOf(
        "fbclid", "gclid", "xtor", "ref", "source", "campaign", "at_medium",
        "at_campaign", "at_custom1", "at_custom2", "mtm_campaign", "mtm_source",
    )

    /**
     * Adresse ramenée à ce qui identifie l'article : sans schéma, sans `www.`,
     * sans ancre ni paramètre de traçage, paramètres restants triés.
     */
    fun cleUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val sansFragment = url.trim().lowercase().substringBefore('#')
        val base = sansFragment.substringBefore('?')
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
        if (base.isBlank()) return null
        val parametres = sansFragment.substringAfter('?', "")
            .split('&')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { p ->
                val nom = p.substringBefore('=')
                nom !in PARAMETRES_IGNORES && !nom.startsWith("utm_")
            }
            .sorted()
        return if (parametres.isEmpty()) base else base + "?" + parametres.joinToString("&")
    }

    /**
     * Retire les articles déjà vus, en gardant la première occurrence (la liste
     * est triée du plus récent au plus ancien).
     *
     * Deux articles sont considérés identiques s'ils pointent vers la même
     * adresse, ou s'ils portent le même titre significatif (assez long pour ne
     * pas être un intitulé générique). Un article sans adresse ni titre
     * exploitable est toujours conservé.
     */
    fun dedoublonner(
        articles: List<fr.fh.rss.data.db.ArticleEntity>,
    ): List<fr.fh.rss.data.db.ArticleEntity> {
        val vues = HashSet<String>()
        val gardes = ArrayList<fr.fh.rss.data.db.ArticleEntity>(articles.size)
        for (article in articles) {
            val cles = buildList {
                cleUrl(article.url)?.let { add("url:$it") }
                val titre = normaliserTitre(article.title)
                if (titre.length >= LONGUEUR_TITRE_CLE) add("titre:$titre")
            }
            if (cles.isEmpty()) {
                gardes.add(article)
                continue
            }
            if (cles.any { it in vues }) continue
            vues.addAll(cles)
            gardes.add(article)
        }
        return gardes
    }

    // ----------------------------------------------------------------- thèmes

    /**
     * Thèmes suivis, avec les mots-clés qui les déclenchent. Volontairement
     * courts et lisibles : un thème qui ne parle pas à Fabrice ne sert à rien.
     */
    val THEMES: List<Pair<String, List<String>>> = listOf(
        "Climat et énergie" to listOf(
            "climat", "carbone", "co2", "gaz à effet de serre", "energie", "énergie",
            "renouvelable", "solaire", "eolien", "éolien", "nucléaire", "chauffage",
            "canicule", "secheresse", "sécheresse", "biodiversite", "biodiversité",
        ),
        "Fiscalité et droit" to listOf(
            "fiscal", "impot", "impôt", "tva", "taxe", "cotisation", "urssaf",
            "conseil d'etat", "conseil d'état", "cassation", "loi de finances",
            "decret", "décret", "arrete", "arrêté", "bofip", "comptable",
        ),
        "Numérique et données" to listOf(
            "cnil", "rgpd", "donnees", "données", "numerique", "numérique", "cyber",
            "intelligence artificielle", " ia ", "algorithme", "cloud", "fuite",
        ),
        "Entreprise et emploi" to listOf(
            "entreprise", "salarie", "salarié", "embauche", "licenciement", "paie",
            "salaire", "greve", "grève", "syndicat", "auto-entrepreneur", "tpe", "pme",
        ),
        "Santé et social" to listOf(
            "sante", "santé", "hopital", "hôpital", "medicament", "médicament",
            "retraite", "mutuelle", "securite sociale", "sécurité sociale", "ehpad",
        ),
        "Logement et urbanisme" to listOf(
            "logement", "loyer", "immobilier", "urbanisme", "permis de construire",
            "copropriete", "copropriété", "habitat", "foncier",
        ),
    )

    const val THEME_AUTRE = "Autres"

    /**
     * Thème dominant d'un article : celui qui totalise le plus de mots-clés
     * trouvés. À égalité, l'ordre de [THEMES] tranche.
     */
    fun themeDe(titreEtTexte: String): String {
        val texte = " " + titreEtTexte
            .replace('\u00a0', ' ')
            .lowercase() + " "
        var meilleur = THEME_AUTRE
        var meilleurScore = 0
        for ((theme, mots) in THEMES) {
            var score = 0
            for (mot in mots) {
                if (texte.contains(mot)) score++
            }
            if (score > meilleurScore) {
                meilleurScore = score
                meilleur = theme
            }
        }
        return meilleur
    }

    /** Articles classés par thème, seuls les thèmes non vides sont rendus. */
    fun parTheme(groupes: List<Groupe>): List<Pair<String, List<Groupe>>> {
        val seaux = LinkedHashMap<String, MutableList<Groupe>>()
        for (groupe in groupes) {
            val texte = groupe.article.title + " " + (groupe.article.textContent.orEmpty().take(600))
            val theme = themeDe(texte)
            seaux.getOrPut(theme) { mutableListOf() }.add(groupe)
        }
        val ordre = THEMES.map { it.first } + THEME_AUTRE
        return seaux.entries
            .sortedBy { ordre.indexOf(it.key).let { index -> if (index < 0) ordre.size else index } }
            .map { it.key to it.value }
    }
}
