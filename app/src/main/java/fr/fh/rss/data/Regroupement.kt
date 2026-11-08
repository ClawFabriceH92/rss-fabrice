package fr.fh.rss.data

import fr.fh.rss.data.db.ArticleRegroupable

// Compilées une seule fois : normaliserTitre est appelée pour chaque article à
// chaque émission de la liste, une Regex construite dans la fonction serait
// recompilée des milliers de fois.
private val ACCENTS_COMBINANTS = Regex("\\p{Mn}+")
private val NON_ALPHANUMERIQUE = Regex("[^\\p{L}\\p{N}]+")

/**
 * Regroupement des articles pour l'onglet IA : dépêches identiques publiées par
 * plusieurs sources, puis classement par thème.
 *
 * Tout est déterministe et testable sur JVM : aucune dépendance à Android ni à
 * un modèle de langage. Le modèle ne sert qu'aux résumés.
 */
object Regroupement {

    /** Un article retenu, avec les sources qui ont publié la même dépêche. */
    data class Groupe<T : ArticleRegroupable>(
        val article: T,
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
            .replace(ACCENTS_COMBINANTS, "")
        var texte = MENTIONS_AGENCE.replace(sansAccents.lowercase(), " ").trim()
        // Les préfixes de type « DIRECT : » ou « Info : » peuvent s'empiler.
        while (true) {
            val avant = texte
            texte = PREFIXES.replace(texte, "").trim()
            if (texte == avant) break
        }
        return texte.replace(NON_ALPHANUMERIQUE, " ").trim()
    }

    /**
     * Regroupe les articles dont le titre normalisé est identique.
     *
     * L'ordre d'entrée est conservé (l'appelant passe la liste déjà triée par
     * date décroissante) : le premier article du groupe est le plus récent.
     */
    fun <T : ArticleRegroupable> regrouper(
        articles: List<T>,
        titresSources: Map<Long, String>,
    ): List<Groupe<T>> {
        val parTitre = LinkedHashMap<String, MutableList<T>>()
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
    fun nombreDoublons(groupes: List<Groupe<*>>): Int = groupes.count { it.doublon }

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

    /** Une autre parution du même article, avec sa source. */
    data class AutreVersion(val articleId: Long, val source: String)

    /**
     * Adresse retenue comme clé d'identité : elle doit viser un article précis.
     * Une adresse sans chemin ni paramètre (« site.com ») est celle de la page
     * d'accueil : certains flux (podcast, agrégateur, pont de réseau social)
     * donnent la même à tous leurs articles, elle ne prouve rien.
     */
    private fun cleUrlIdentifiante(url: String?): String? =
        cleUrl(url)?.takeIf { it.contains('/') || it.contains('?') }

    /** Clés d'identité d'un article : adresse nettoyée et titre significatif. */
    private fun cles(article: ArticleRegroupable): List<String> = buildList {
        cleUrlIdentifiante(article.url)?.let { add("url:$it") }
        val titre = normaliserTitre(article.title)
        if (titre.length >= LONGUEUR_TITRE_CLE) add("titre:$titre")
    }

    /**
     * Pour chaque article, les autres parutions du même sujet (autres sources),
     * afin d'afficher « vu aussi sur … » dans la liste.
     */
    fun autresVersions(
        articles: List<ArticleRegroupable>,
        titresSources: Map<Long, String>,
    ): Map<Long, List<AutreVersion>> {
        val parCle = HashMap<String, MutableList<ArticleRegroupable>>()
        for (article in articles) {
            for (cle in cles(article)) {
                parCle.getOrPut(cle) { mutableListOf() }.add(article)
            }
        }
        val sortie = HashMap<Long, List<AutreVersion>>()
        for (article in articles) {
            val vus = LinkedHashSet<Long>()
            val autres = mutableListOf<AutreVersion>()
            for (cle in cles(article)) {
                for (meme in parCle[cle].orEmpty()) {
                    // Deux articles de la même source restent deux articles.
                    if (meme.id == article.id || meme.feedId == article.feedId) continue
                    if (!vus.add(meme.id)) continue
                    autres += AutreVersion(
                        articleId = meme.id,
                        source = titresSources[meme.feedId] ?: "source inconnue",
                    )
                }
            }
            if (autres.isNotEmpty()) sortie[article.id] = autres
        }
        return sortie
    }

    /**
     * Retire les articles déjà vus, en gardant la première occurrence (la liste
     * est triée du plus récent au plus ancien).
     *
     * Deux articles sont considérés identiques s'ils pointent vers la même
     * adresse (précise : pas celle d'une page d'accueil), ou s'ils portent le
     * même titre significatif (assez long pour ne pas être un intitulé
     * générique), ET s'ils viennent de sources différentes : deux articles
     * d'une même source au titre identique (rubrique hebdomadaire) sont deux
     * articles. Un article sans clé exploitable est toujours conservé.
     */
    fun <T : ArticleRegroupable> dedoublonner(
        articles: List<T>,
    ): List<T> {
        // Pour chaque clé, les sources dont un article est déjà retenu.
        val sourcesParCle = HashMap<String, MutableSet<Long>>()
        val gardes = ArrayList<T>(articles.size)
        for (article in articles) {
            val cles = cles(article)
            if (cles.isEmpty()) {
                gardes.add(article)
                continue
            }
            val dejaVu = cles.any { cle ->
                sourcesParCle[cle]?.any { it != article.feedId } == true
            }
            if (dejaVu) continue
            for (cle in cles) sourcesParCle.getOrPut(cle) { HashSet() }.add(article.feedId)
            gardes.add(article)
        }
        return gardes
    }

    /** Nombre d'articles que la liste afficherait pour ces articles (doublons masqués exclus). */
    fun nombreAffiche(articles: List<ArticleRegroupable>): Int = dedoublonner(articles).size

    /** Clé des articles dont la source n'est dans aucun dossier (comme dans les compteurs SQL). */
    const val SANS_DOSSIER = -1L

    /**
     * Nombre d'articles affichés par dossier, doublons masqués exclus. Le
     * dédoublonnage se fait à l'intérieur de chaque dossier : c'est la liste
     * que l'on voit en l'ouvrant. [dossierDeSource] associe chaque source à son
     * dossier (null : aucun) ; un article d'une source inconnue n'est compté nulle part.
     */
    fun nombreParDossier(
        articles: List<ArticleRegroupable>,
        dossierDeSource: Map<Long, Long?>,
    ): Map<Long, Int> = articles
        .filter { dossierDeSource.containsKey(it.feedId) }
        .groupBy { dossierDeSource[it.feedId] ?: SANS_DOSSIER }
        .mapValues { (_, liste) -> nombreAffiche(liste) }

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
    fun <T : ArticleRegroupable> parTheme(groupes: List<Groupe<T>>): List<Pair<String, List<Groupe<T>>>> {
        val seaux = LinkedHashMap<String, MutableList<Groupe<T>>>()
        for (groupe in groupes) {
            val texte = groupe.article.title + " " + groupe.article.texteApercu
            val theme = themeDe(texte)
            seaux.getOrPut(theme) { mutableListOf() }.add(groupe)
        }
        val ordre = THEMES.map { it.first } + THEME_AUTRE
        return seaux.entries
            .sortedBy { ordre.indexOf(it.key).let { index -> if (index < 0) ordre.size else index } }
            .map { it.key to it.value }
    }
}
