package fr.fh.rss.data.net

/**
 * Catalogue de sources vérifiées, embarqué dans l'application.
 *
 * Chaque adresse a été interrogée à la construction de cette liste : elle
 * répondait du XML avec au moins un article. C'est la différence avec les
 * propositions d'un modèle de langage, qui invente une grande partie des
 * adresses de flux (mesuré : 0 adresse valide sur 13 avec ministral-3:8b).
 *
 * Le catalogue sert deux usages : parcourir les sources par thème, et donner au
 * modèle local une liste fermée dans laquelle choisir selon les sources déjà
 * suivies. Dans ce second usage, aucune adresse inventée ne peut entrer.
 *
 * Un flux peut mourir : l'ajout reste vérifié par le dépôt, et une source
 * ajoutée qui ne répond plus se retrouve dans l'onglet « Flux en erreur ».
 */
object CatalogueSources {

    data class Entree(val nom: String, val url: String, val theme: String)

    /** Thèmes dans l'ordre d'affichage. */
    val THEMES: List<String> = listOf(
        "Compta et audit",
        "Droit et fiscal",
        "Social et paie",
        "Économie",
        "Actualité",
        "Développement",
        "IA",
        "Veille et OSINT",
    )

    val entrees: List<Entree> = listOf(
            // ---- Compta et audit
            Entree("Ordre des experts-comptables", "https://www.experts-comptables.fr/rss.xml", "Compta et audit"),
            Entree("ANC (normes comptables)", "https://www.anc.gouv.fr/rss.xml", "Compta et audit"),
            Entree("Cour des comptes", "https://www.ccomptes.fr/fr/rss/actualites", "Compta et audit"),
            Entree("Compta Facile", "https://www.compta-facile.com/feed/", "Compta et audit"),
            // ---- Droit et fiscal
            Entree("Dalloz actualité", "https://www.dalloz-actualite.fr/rss.xml", "Droit et fiscal"),
            Entree("LegalIs (droit du numérique)", "https://www.legalis.net/feed/", "Droit et fiscal"),
            Entree("Village de la Justice", "https://www.village-justice.com/articles/spip.php?page=backend", "Droit et fiscal"),
            Entree("BOFiP (actualités)", "http://bofip.impots.gouv.fr/bofip/ext/rss/actus-rss.xml", "Droit et fiscal"),
            Entree("economie.gouv.fr (actualités)", "https://www.economie.gouv.fr/rss/toutesactualites", "Droit et fiscal"),
            Entree("DGCCRF", "https://www.economie.gouv.fr/dgccrf/rss", "Droit et fiscal"),
            Entree("Tracfin", "https://www.economie.gouv.fr/tracfin/rss", "Droit et fiscal"),
            Entree("entreprises.gouv.fr", "https://www.entreprises.gouv.fr/rss.xml", "Droit et fiscal"),
            Entree("Autorité de la concurrence", "https://www.autoritedelaconcurrence.fr/rss.xml", "Droit et fiscal"),
            Entree("CNIL", "https://www.cnil.fr/fr/rss.xml", "Droit et fiscal"),
            // ---- Social et paie
            Entree("Ministère du Travail", "https://travail-emploi.gouv.fr/rss.xml", "Social et paie"),
            Entree("Net-entreprises", "https://www.net-entreprises.fr/feed/", "Social et paie"),
            Entree("Agirc-Arrco", "https://www.agirc-arrco.fr/feed/?feed=communiques", "Social et paie"),
            // ---- Économie
            Entree("Le Monde Économie", "https://www.lemonde.fr/economie/rss_full.xml", "Économie"),
            Entree("Le Figaro Économie", "https://www.lefigaro.fr/rss/figaro_economie.xml", "Économie"),
            Entree("La Tribune", "https://www.latribune.fr/feed.xml", "Économie"),
            Entree("Alternatives Économiques", "https://www.alternatives-economiques.fr/rss.xml", "Économie"),
            Entree("Trésor-Info (Tresor-Eco)", "https://www.tresor.economie.gouv.fr/Flux/Atom/Articles/Tags/Tresor-Eco", "Économie"),
            Entree("Stratégie et plan", "https://www.strategie-plan.gouv.fr/rss.xml", "Économie"),
            Entree("Capital", "https://feed.prismamediadigital.com/v1/cap/rss?limit=20", "Économie"),
            // ---- Actualité
            Entree("Le Monde (une)", "https://www.lemonde.fr/rss/une.xml", "Actualité"),
            Entree("France Info", "https://www.francetvinfo.fr/titres.rss", "Actualité"),
            Entree("20 Minutes", "https://www.20minutes.fr/feeds/rss-une.xml", "Actualité"),
            Entree("Le Parisien", "https://feeds.leparisien.fr/leparisien/rss", "Actualité"),
            Entree("Slate.fr", "https://www.slate.fr/rss.xml", "Actualité"),
            Entree("Usbek et Rica", "https://usbeketrica.com/rss", "Actualité"),
            Entree("France Culture", "https://www.radiofrance.fr/franceculture/rss", "Actualité"),
            Entree("Mediapart", "https://www.mediapart.fr/articles/feed", "Actualité"),
            Entree("Reporterre", "https://reporterre.net/spip.php?page=backend", "Actualité"),
            // ---- Développement
            Entree("Android Developers Blog", "https://android-developers.googleblog.com/feeds/posts/default", "Développement"),
            Entree("Kotlin (JetBrains)", "https://blog.jetbrains.com/kotlin/feed/", "Développement"),
            Entree("JetBrains IDEA", "https://blog.jetbrains.com/idea/feed/", "Développement"),
            Entree("ProAndroidDev", "https://proandroiddev.com/feed", "Développement"),
            Entree("Next (next.ink)", "https://next.ink/feed/", "Développement"),
            Entree("Journal du Hacker", "https://www.journalduhacker.net/rss", "Développement"),
            Entree("Developpez.com", "https://www.developpez.com/index/rss", "Développement"),
            Entree("LinuxFr", "https://linuxfr.org/news.atom", "Développement"),
            Entree("Numerama", "https://www.numerama.com/feed/", "Développement"),
            Entree("Le Monde Informatique", "https://www.lemondeinformatique.fr/rss.xml", "Développement"),
            Entree("Korben", "https://korben.info/feed", "Développement"),
            Entree("Hacker News (frontpage)", "https://hnrss.org/frontpage", "Développement"),
            Entree("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "Développement"),
            Entree("LWN", "https://lwn.net/headlines/rss", "Développement"),
            Entree("La Quadrature du Net", "http://www.laquadrature.net/fr/rss.xml", "Développement"),
            Entree("April (revue de presse)", "https://www.april.org/revue-de-presse/feed", "Développement"),
            Entree("Les Outils Collaboratifs", "https://outilscollaboratifs.com/feed/", "Développement"),
            // ---- IA
            Entree("Ollama (blog)", "https://ollama.com/blog/rss.xml", "IA"),
            Entree("Simon Willison", "https://simonwillison.net/atom/everything/", "IA"),
            Entree("Google AI Blog", "https://blog.google/technology/ai/rss/", "IA"),
            Entree("Hugging Face (blog)", "https://huggingface.co/blog/feed.xml", "IA"),
            Entree("OpenAI (news)", "https://openai.com/news/rss.xml", "IA"),
            Entree("Hacker News (AI)", "https://hnrss.org/newest?q=LLM", "IA"),
            // ---- Veille et OSINT
            Entree("Bellingcat", "https://www.bellingcat.com/feed/", "Veille et OSINT"),
            Entree("Krebs on Security", "https://krebsonsecurity.com/feed/", "Veille et OSINT"),
            Entree("The Record", "https://therecord.media/feed", "Veille et OSINT"),
            Entree("CyberScoop", "https://www.cyberscoop.com/feed/", "Veille et OSINT"),
            Entree("Inserm", "https://www.inserm.fr/feed", "Veille et OSINT"),
    )

    fun parTheme(theme: String): List<Entree> = entrees.filter { it.theme == theme }

    /** Clé de comparaison d'une adresse : schéma et casse ignorés, barre finale retirée. */
    fun cle(url: String): String = SuggestionsSources.cle(url)

    /** Entrées du catalogue dont l'adresse n'est pas déjà suivie. */
    fun nonSuivies(suivies: List<String>, theme: String? = null): List<Entree> {
        val connues = suivies.map(::cle).toHashSet()
        return entrees
            .filter { theme == null || it.theme == theme }
            .filter { cle(it.url) !in connues }
    }

    /** Nombre d'entrées par thème, pour les libellés de filtre. */
    fun compteParTheme(): Map<String, Int> = entrees.groupingBy { it.theme }.eachCount()

    /**
     * Consigne envoyée au modèle : la liste fermée numérotée, et la demande de
     * répondre par les numéros. Un numéro est vérifiable, un nom recopié ne
     * l'est pas (un petit modèle le déforme).
     */
    fun consigne(candidats: List<Entree>, nombre: Int = 8): String = buildString {
        appendLine("Voici un catalogue de sources d'information numérotées :")
        candidats.forEachIndexed { i, e -> appendLine("${i + 1}. ${e.nom} (thème : ${e.theme})") }
        appendLine()
        append(
            "Choisis les $nombre sources les plus utiles pour quelqu'un qui suit déjà les " +
                "sources listées par l'utilisateur. Réponds uniquement par les numéros, " +
                "séparés par des virgules, sans texte autour.",
        )
    }

    /**
     * Entrées correspondant aux numéros renvoyés par le modèle. Les numéros hors
     * catalogue sont ignorés, les doublons retirés : on ne retient jamais autre
     * chose que ce qui était proposé.
     */
    fun depuisNumeros(reponse: String, candidats: List<Entree>): List<Entree> {
        val vus = LinkedHashSet<Int>()
        for (m in Regex("""[0-9]{1,3}""").findAll(reponse)) {
            val n = m.value.toIntOrNull() ?: continue
            if (n in 1..candidats.size) vus += n
        }
        return vus.map { candidats[it - 1] }
    }

    /** Repli quand le modèle recopie des noms au lieu des numéros. */
    fun depuisNoms(reponse: String, candidats: List<Entree>): List<Entree> {
        val texte = reponse.lowercase()
        return candidats.filter { e ->
            val nom = e.nom.lowercase()
            texte.contains(nom) || nom.split(" ", "(", ")", "-").any { mot ->
                mot.length > 4 && texte.contains(mot)
            }
        }
    }
}
