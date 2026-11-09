package fr.fh.rss.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.fh.rss.RssApp
import fr.fh.rss.data.ApercuArchive
import fr.fh.rss.data.Avertissements
import fr.fh.rss.data.LectureOpml
import fr.fh.rss.data.Nettoyage
import fr.fh.rss.data.Filtre
import fr.fh.rss.data.Regroupement
import fr.fh.rss.data.Reglages
import fr.fh.rss.data.db.ArticleEntity
import fr.fh.rss.data.db.ArticleLigne
import fr.fh.rss.data.db.FeedEntity
import fr.fh.rss.data.db.FolderEntity
import fr.fh.rss.data.model.AddResult
import fr.fh.rss.data.model.FeedUnread
import fr.fh.rss.data.model.RefreshSummary
import fr.fh.rss.work.RefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ce que l'écran principal affiche. */
data class Selection(val filtre: Filtre, val titre: String)

/**
 * Vue affichée à l'ouverture de l'application : les articles non lus. Un article
 * lu sort donc de la liste, c'est le comportement attendu quand on trie son
 * flux d'arrivée ; la vue « Tous les articles » reste à un appui dans le tiroir.
 */
val SELECTION_PAR_DEFAUT = Selection(Filtre.NonLus, "Non lus")

/** Vues qui ne dépendent ni d'un dossier ni d'une source : rien à réinitialiser. */
private val VUES_D_ENSEMBLE: Set<Filtre> = setOf(Filtre.Tous, Filtre.NonLus, Filtre.Favoris)

/** Articles que « Tout marquer comme lu » vient de passer à lus : de quoi annuler. */
data class AnnulationLecture(val ids: List<Long>)

/** Taille d'une page de liste, et plafond pour ne pas tout charger en mémoire. */
const val LIMITE_PAGE = 500
const val LIMITE_MAX = 5000

/**
 * « Charger plus » n'a de sens que si la page demandée est pleine (il peut y
 * avoir la suite) et que le plafond n'est pas atteint : au plafond, le bouton
 * ne ferait plus rien.
 */
internal fun peutChargerPlus(affiches: Int, limite: Int): Boolean =
    affiches >= limite && limite < LIMITE_MAX

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val conteneur = (app as RssApp).container
    private val depot = conteneur.repository
    private val reglagesDepot = conteneur.settings

    val reglages: StateFlow<Reglages> = reglagesDepot.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, Reglages())

    val sources: StateFlow<List<FeedEntity>> = depot.observeFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dossiers: StateFlow<List<FolderEntity>> = depot.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nonLusParSource: StateFlow<Map<Long, Int>> = depot.observeUnread()
        .map { liste: List<FeedUnread> -> liste.associate { it.feedId to it.nombre } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Non lus, réduits à de quoi dédoublonner : les compteurs de dossier et le
     * total comptent ce que la liste « Non lus » affiche, pas les lignes SQL.
     * Les doublons d'autres sources, masqués dans la liste, ne gonflent plus
     * le badge. (Les compteurs par source restent ceux de la base : le
     * dédoublonnage ne joue qu'entre sources différentes.)
     */
    private val nonLusCles = depot.observeNonLusCles()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val nonLusParDossier: StateFlow<Map<Long, Int>> =
        combine(nonLusCles, depot.observeFeeds()) { cles, flux ->
            Regroupement.nombreParDossier(cles, flux.associate { it.id to it.folderId })
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val nonLusTotal: StateFlow<Int> = nonLusCles
        .map { Regroupement.nombreAffiche(it) }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _selection = MutableStateFlow(SELECTION_PAR_DEFAUT)
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    private val _recherche = MutableStateFlow("")
    val recherche: StateFlow<String> = _recherche.asStateFlow()

    /** Nombre d'articles demandés à la base ; augmente par « Charger plus ». */
    private val _limite = MutableStateFlow(LIMITE_PAGE)
    val limite: StateFlow<Int> = _limite.asStateFlow()

    val articles: StateFlow<List<ArticleLigne>> =
        combine(_selection, _recherche, _limite) { sel, requete, limite ->
            Triple(sel, requete, limite)
        }
            .flatMapLatest { (sel, requete, limite) ->
                if (requete.isBlank()) depot.observeArticles(sel.filtre, limite)
                else depot.observeSearch(requete, limite)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Liste affichée dans l'accueil : un même article publié par plusieurs
     * sources n'apparaît qu'une fois. `articles` reste la liste brute, dont
     * l'onglet IA a besoin pour montrer qui a publié quoi.
     */
    val articlesAffiches: StateFlow<List<ArticleLigne>> = articles
        .map { Regroupement.dedoublonner(it) }
        // Le dédoublonnage parcourt jusqu'à 5 000 articles : pas sur le thread principal.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Autres parutions du même article (même adresse ou même titre), source par
     * source : sert au « vu aussi sur … » de la liste et du lecteur.
     */
    val autresVersions: StateFlow<Map<Long, List<Regroupement.AutreVersion>>> =
        combine(articles, sources) { liste, flux ->
            Regroupement.autresVersions(
                liste,
                flux.associate { it.id to it.title },
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _enCours = MutableStateFlow(false)
    val enCours: StateFlow<Boolean> = _enCours.asStateFlow()

    /** Avancement de la collecte : sources traitées / sources à traiter. */
    private val _progres = MutableStateFlow<ProgresCollecte?>(null)
    val progres: StateFlow<ProgresCollecte?> = _progres.asStateFlow()

    /** Dernier bilan de collecte : sert à la pastille animée de l'écran. */
    private val _dernierBilan = MutableStateFlow<RefreshSummary?>(null)
    val dernierBilan: StateFlow<RefreshSummary?> = _dernierBilan.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Dernier marquage « lu » en masse, proposé à l'annulation par une Snackbar. */
    private val _annulationLecture = MutableStateFlow<AnnulationLecture?>(null)
    val annulationLecture: StateFlow<AnnulationLecture?> = _annulationLecture.asStateFlow()

    // ------------------------------------------------------------ onglet IA
    private val ollama = fr.fh.rss.data.net.Ollama()

    private val _resumes = MutableStateFlow<Map<Long, String>>(emptyMap())
    val resumes: StateFlow<Map<Long, String>> = _resumes.asStateFlow()

    private val _resumeEnCours = MutableStateFlow<Long?>(null)
    val resumeEnCours: StateFlow<Long?> = _resumeEnCours.asStateFlow()

    private val _synthese = MutableStateFlow<String?>(null)
    val synthese: StateFlow<String?> = _synthese.asStateFlow()

    private val _iaEnCours = MutableStateFlow(false)
    val iaEnCours: StateFlow<Boolean> = _iaEnCours.asStateFlow()

    private val _modelesOllama = MutableStateFlow<List<String>>(emptyList())
    val modelesOllama: StateFlow<List<String>> = _modelesOllama.asStateFlow()

    private val _ollamaEtat = MutableStateFlow<String?>(null)
    val ollamaEtat: StateFlow<String?> = _ollamaEtat.asStateFlow()

    /**
     * Disponibilité du serveur Ollama : `null` tant qu'aucun test n'a eu lieu,
     * `true` si le serveur répond à l'API, `false` sinon. Sert à colorer
     * l'onglet IA en vert ou en rouge.
     */
    private val _iaDisponible = MutableStateFlow<Boolean?>(null)
    val iaDisponible: StateFlow<Boolean?> = _iaDisponible.asStateFlow()

    // ------------------------------------------- suggestions de sources (IA)
    private val _suggestions = MutableStateFlow<List<fr.fh.rss.data.net.SuggestionsSources.Proposition>>(emptyList())
    val suggestions: StateFlow<List<fr.fh.rss.data.net.SuggestionsSources.Proposition>> = _suggestions.asStateFlow()

    private val _suggestionsEnCours = MutableStateFlow(false)
    val suggestionsEnCours: StateFlow<Boolean> = _suggestionsEnCours.asStateFlow()

    private val _suggestionsErreur = MutableStateFlow<String?>(null)
    val suggestionsErreur: StateFlow<String?> = _suggestionsErreur.asStateFlow()

    private val _themeCatalogue = MutableStateFlow(fr.fh.rss.data.net.CatalogueSources.THEMES.first())

    /** Sources du catalogue pour le thème courant, moins celles déjà suivies. */
    val catalogueSources: StateFlow<List<fr.fh.rss.data.net.CatalogueSources.Entree>> =
        combine(sources, _themeCatalogue) { feeds, theme ->
            fr.fh.rss.data.net.CatalogueSources.nonSuivies(feeds.map { it.url }, theme)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val themesCatalogue: List<String> = fr.fh.rss.data.net.CatalogueSources.THEMES
    val themeCatalogue: StateFlow<String> = _themeCatalogue.asStateFlow()

    private var collecteFaite = false

    // ------------------------------------------------------------- nettoyage
    private val _nettoyage = MutableStateFlow<List<Nettoyage.Proposition>>(emptyList())
    val nettoyage: StateFlow<List<Nettoyage.Proposition>> = _nettoyage.asStateFlow()

    private val _nettoyageEnCours = MutableStateFlow(false)
    val nettoyageEnCours: StateFlow<Boolean> = _nettoyageEnCours.asStateFlow()

    /** Recalcule les sources proposées au retrait (appelé à l'ouverture de l'onglet). */
    fun analyserNettoyage() {
        if (_nettoyageEnCours.value) return
        viewModelScope.launch {
            _nettoyageEnCours.value = true
            _nettoyage.value = runCatching { depot.propositionsNettoyage() }.getOrDefault(emptyList())
            _nettoyageEnCours.value = false
        }
    }

    // ------------------------------------------------------------ actions

    fun changerSelection(sel: Selection) {
        _selection.value = sel
        _recherche.value = ""
        _limite.value = LIMITE_PAGE
    }

    fun setRecherche(texte: String) {
        _recherche.value = texte
        _limite.value = LIMITE_PAGE
    }

    /** Affiche une page de plus : la base n'en renvoie que [limite] à la fois. */
    fun chargerPlus() {
        _limite.value = (_limite.value + LIMITE_PAGE).coerceAtMost(LIMITE_MAX)
    }

    /**
     * Collecte systématique au lancement, puis à la demande. Une actualisation
     * demandée par l'utilisateur passe [ignorerRepos] à vrai : elle réinterroge
     * aussi les sources au repos ; la collecte automatique les laisse tranquilles.
     */
    fun rafraichir(silencieux: Boolean = false, ignorerRepos: Boolean = false) {
        if (_enCours.value) return
        viewModelScope.launch {
            _enCours.value = true
            _dernierBilan.value = null
            _progres.value = ProgresCollecte(0, 0)
            val bilan = runCatching {
                depot.refreshAll(
                    ignorerRepos = ignorerRepos,
                    onProgres = { faits, total ->
                        _progres.value = ProgresCollecte(faits, total)
                    },
                )
            }.getOrElse { RefreshSummary(echecs = listOf("collecte" to (it.message ?: "erreur"))) }
            _enCours.value = false
            _progres.value = null
            // Le succès passe par la pastille animée ; la barre de messages est
            // réservée aux échecs, pour ne pas dire deux fois la même chose.
            // Hors connexion, la pastille dit « Pas de connexion » : rien de plus.
            // Une source qui échoue depuis le seuil de proposition est signalée
            // à part : c'est une décision à prendre, pas une panne passagère.
            val propositions = runCatching { depot.sourcesAExaminer() }.getOrDefault(emptyList())
            _message.value = when {
                propositions.isNotEmpty() ->
                    "${propositions.size} source(s) en échec depuis " +
                        "${fr.fh.rss.data.EtatSource.SEUIL_PROPOSITION_JOURS} jours ou plus : " +
                        "voir Réglages, Sources en échec."
                bilan.echecs.isEmpty() -> null
                else -> "${bilan.echecs.size} source(s) en échec : " +
                    bilan.echecs.take(2).joinToString(", ") { "${it.first} (${it.second})" }
            }
            _dernierBilan.value = bilan
        }
    }

    fun effacerBilan() {
        _dernierBilan.value = null
    }

    /** Lancée une seule fois par processus, à la première ouverture. */
    fun rafraichirAuLancement() {
        if (collecteFaite) return
        collecteFaite = true
        rafraichir(silencieux = true)
    }

    fun ajouterFlux(url: String, dossierId: Long?) {
        if (url.isBlank()) {
            _message.value = "Adresse vide."
            return
        }
        viewModelScope.launch {
            _enCours.value = true
            val resultat = depot.addFeed(url, dossierId)
            _enCours.value = false
            _message.value = when (resultat) {
                is AddResult.Added ->
                    "« ${resultat.title} » ajouté (${resultat.count} article(s))."
                is AddResult.Already -> "« ${resultat.title} » est déjà dans vos sources."
                is AddResult.Failed -> "Ajout impossible : ${resultat.message}"
            }
            if (_selection.value.filtre !in VUES_D_ENSEMBLE) {
                _selection.value = SELECTION_PAR_DEFAUT
            }
        }
    }

    /**
     * Demande des idées de sources au modèle local, puis vérifie chaque adresse
     * par le réseau : un modèle cite volontiers des flux qui n'existent pas, et
     * seules les adresses qui répondent vraiment un flux sont proposées à
     * l'ajout. Rien n'est abonné sans un appui de l'utilisateur.
     */
    fun suggererSources() {
        if (_suggestionsEnCours.value) return
        viewModelScope.launch {
            _suggestionsEnCours.value = true
            _suggestionsErreur.value = null
            val courant = reglages.value
            runCatching {
                val existantes = depot.toutesLesSources().map { it.title to it.url }
                val reponse = ollama.demander(
                    base = courant.ollamaUrl,
                    modele = courant.ollamaModele,
                    consigne = fr.fh.rss.data.net.SuggestionsSources.consigne(),
                    contenu = fr.fh.rss.data.net.SuggestionsSources.contenu(existantes),
                    nombreTokens = 900,
                )
                val candidates = fr.fh.rss.data.net.SuggestionsSources.extraire(reponse)
                if (candidates.isEmpty()) {
                    _suggestionsErreur.value =
                        "Le modèle n'a cité aucune adresse. Réessaie ou change de modèle dans les réglages."
                    _suggestions.value = emptyList()
                } else {
                    _suggestions.value = depot.verifierPropositions(candidates)
                }
            }.onFailure { erreur ->
                _suggestionsErreur.value = fr.fh.rss.data.net.Ollama.messageErreur(courant.ollamaUrl, erreur)
                _iaDisponible.value = false
            }
            _suggestionsEnCours.value = false
        }
    }

    /** Abonne une proposition vérifiée, puis la retire de la liste. */
    fun ajouterSuggestion(proposition: fr.fh.rss.data.net.SuggestionsSources.Proposition) {
        viewModelScope.launch {
            val resultat = depot.addFeed(proposition.url, null)
            _message.value = when (resultat) {
                is AddResult.Added -> "« ${resultat.title} » ajouté (${resultat.count} article(s))."
                is AddResult.Already -> "« ${resultat.title} » est déjà dans vos sources."
                is AddResult.Failed -> "Ajout impossible : ${resultat.message}"
            }
            _suggestions.value = _suggestions.value.filterNot {
                fr.fh.rss.data.net.SuggestionsSources.cle(it.url) ==
                    fr.fh.rss.data.net.SuggestionsSources.cle(proposition.url)
            }
            if (_selection.value.filtre !in VUES_D_ENSEMBLE) _selection.value = SELECTION_PAR_DEFAUT
        }
    }

    fun setThemeCatalogue(theme: String) {
        if (theme in themesCatalogue) _themeCatalogue.value = theme
    }

    /**
     * Le modèle choisit dans le catalogue embarqué : les adresses sont déjà
     * vérifiées, il ne peut donc rien inventer. Il ne renvoie que des numéros,
     * que l'application traduit en sources.
     */
    fun choisirDansCatalogue() {
        if (_suggestionsEnCours.value) return
        viewModelScope.launch {
            _suggestionsEnCours.value = true
            _suggestionsErreur.value = null
            val courant = reglages.value
            val suivies = sources.value
            val candidats = fr.fh.rss.data.net.CatalogueSources.nonSuivies(suivies.map { it.url })
            if (candidats.isEmpty()) {
                _suggestionsErreur.value = "Toutes les sources du catalogue sont déjà suivies."
                _suggestionsEnCours.value = false
                return@launch
            }
            runCatching {
                val reponse = ollama.demander(
                    base = courant.ollamaUrl,
                    modele = courant.ollamaModele,
                    consigne = fr.fh.rss.data.net.CatalogueSources.consigne(candidats),
                    contenu = fr.fh.rss.data.net.SuggestionsSources.contenu(
                        suivies.map { it.title to it.url },
                    ),
                    nombreTokens = 300,
                )
                var choix = fr.fh.rss.data.net.CatalogueSources.depuisNumeros(reponse, candidats)
                if (choix.isEmpty()) {
                    choix = fr.fh.rss.data.net.CatalogueSources.depuisNoms(reponse, candidats)
                }
                _suggestions.value = choix.map {
                    fr.fh.rss.data.net.SuggestionsSources.Proposition(
                        nom = it.nom,
                        url = it.url,
                        etat = fr.fh.rss.data.net.SuggestionsSources.Etat.PROPOSE,
                        detail = "catalogue · ${it.theme}",
                    )
                }
                if (choix.isEmpty()) {
                    _suggestionsErreur.value =
                        "Le modèle n'a rien retenu du catalogue. Réessaie ou change de modèle."
                }
            }.onFailure { erreur ->
                _suggestionsErreur.value = fr.fh.rss.data.net.Ollama.messageErreur(courant.ollamaUrl, erreur)
                _iaDisponible.value = false
            }
            _suggestionsEnCours.value = false
        }
    }

    fun effacerSuggestions() {
        _suggestions.value = emptyList()
        _suggestionsErreur.value = null
    }

    fun creerDossier(nom: String) {
        viewModelScope.launch {
            val id = depot.createFolder(nom)
            if (id < 0) _message.value = "Nom de dossier invalide."
        }
    }

    fun renommerDossier(id: Long, nom: String) {
        viewModelScope.launch { depot.renameFolder(id, nom) }
    }

    fun supprimerDossier(id: Long) {
        viewModelScope.launch {
            depot.deleteFolder(id)
            if ((_selection.value.filtre as? Filtre.Dossier)?.id == id) {
                _selection.value = SELECTION_PAR_DEFAUT
            }
            _message.value = "Dossier supprimé (les sources sont conservées)."
        }
    }

    fun deplacerFlux(feedId: Long, dossierId: Long?) {
        viewModelScope.launch {
            depot.moveFeed(feedId, dossierId)
            _message.value = if (dossierId == null) "Source déplacée hors dossier." else "Source déplacée."
        }
    }

    /** Favoris que la suppression de ces sources laisserait en place (pour la confirmation). */
    suspend fun nombreFavoris(feedIds: Collection<Long>): Int =
        runCatching { depot.nombreFavoris(feedIds) }.getOrDefault(0)

    /** Ce que ferait la limite d'archive [limite] (pour avertir avant de réduire). */
    suspend fun apercuArchive(limite: Int): ApercuArchive = depot.apercuArchive(limite)

    /** Phrase de fin de suppression, avec le nombre de favoris conservés s'il y en a. */
    private fun messageSuppression(supprimees: Int, favoris: Int): String {
        val base = when {
            supprimees == 0 -> return "Aucune source supprimée."
            supprimees == 1 -> "1 source supprimée"
            else -> "$supprimees sources supprimées"
        }
        return when (favoris) {
            0 -> "$base."
            1 -> "$base, 1 favori conservé."
            else -> "$base, $favoris favoris conservés."
        }
    }

    fun supprimerFlux(feedId: Long) {
        viewModelScope.launch {
            val favoris = nombreFavoris(listOf(feedId))
            depot.deleteFeed(feedId)
            if ((_selection.value.filtre as? Filtre.Flux)?.id == feedId) {
                _selection.value = SELECTION_PAR_DEFAUT
            }
            _message.value = messageSuppression(1, favoris)
        }
    }

    /**
     * Supprime d'un coup toutes les sources en échec (paramétrage, onglet
     * « Flux en erreur »). Les articles déjà téléchargés partent avec elles,
     * sauf les favoris.
     */
    fun supprimerFluxEnEchec(feedIds: List<Long>) {
        if (feedIds.isEmpty()) return
        viewModelScope.launch {
            val favoris = nombreFavoris(feedIds)
            val supprimees = runCatching { depot.deleteFeeds(feedIds) }.getOrDefault(0)
            // Les propositions de nettoyage qui viennent d'être traitées disparaissent.
            _nettoyage.value = _nettoyage.value.filterNot { it.id in feedIds }
            val filtre = _selection.value.filtre
            if (filtre is Filtre.Flux && filtre.id in feedIds) {
                _selection.value = SELECTION_PAR_DEFAUT
            }
            _message.value = messageSuppression(supprimees, favoris)
        }
    }

    fun marquerSourceLue(feedId: Long) {
        viewModelScope.launch { depot.markFeedRead(feedId) }
    }

    /**
     * Marque comme lus les articles non lus de [filtre] (par défaut la sélection
     * courante, pas tout le parc) et propose d'annuler.
     */
    fun marquerToutLu(filtre: Filtre = _selection.value.filtre) {
        viewModelScope.launch {
            val ids = depot.marquerSelectionLue(filtre)
            if (ids.isEmpty()) {
                _message.value = "Aucun article non lu à marquer."
            } else {
                _annulationLecture.value = AnnulationLecture(ids)
            }
        }
    }

    /** Remet à « non lu » les articles du dernier marquage en masse. */
    fun annulerMarquageLu(ids: List<Long>) {
        viewModelScope.launch { depot.annulerMarquageLu(ids) }
    }

    fun effacerAnnulationLecture() {
        _annulationLecture.value = null
    }

    /**
     * Doublons masqués d'un article affiché (même article chez d'autres
     * sources) : les lire ou les balayer avec lui évite des non lus fantômes
     * que la liste ne montre jamais.
     */
    private fun doublonsMasques(articleId: Long): List<Long> =
        autresVersions.value[articleId].orEmpty().map { it.articleId }

    fun basculerLu(article: ArticleLigne) {
        val ids = listOf(article.id) + doublonsMasques(article.id)
        viewModelScope.launch { depot.setReadGroupe(ids, !article.isRead) }
    }

    fun marquerLu(articleId: Long) {
        val ids = listOf(articleId) + doublonsMasques(articleId)
        viewModelScope.launch { depot.setReadGroupe(ids, true) }
    }

    fun basculerFavori(article: ArticleLigne) {
        basculerFavori(article.id, !article.isStarred)
    }

    /**
     * Mettre de côté promet la lecture hors connexion : au passage en favori,
     * le texte complet est téléchargé si le réseau est là (sinon la collecte
     * suivante s'en chargera).
     */
    fun basculerFavori(articleId: Long, favori: Boolean) {
        viewModelScope.launch {
            depot.setStarred(articleId, favori)
            if (favori) runCatching { depot.fetchFullContent(articleId) }
        }
    }

    fun observerArticle(id: Long): Flow<ArticleEntity?> = depot.observeArticle(id)

    /** Télécharge le texte complet pour la lecture hors connexion. */
    fun completerArticle(articleId: Long) {
        viewModelScope.launch { depot.fetchFullContent(articleId) }
    }

    /**
     * Import depuis le fichier choisi : la lecture se fait hors du thread principal
     * et un fichier illisible est signalé, au lieu de ne rien faire.
     */
    fun importerOpmlDepuis(uri: android.net.Uri) {
        viewModelScope.launch {
            val texte = withContext(Dispatchers.IO) {
                runCatching {
                    LectureOpml.lire(getApplication<Application>().contentResolver.openInputStream(uri))
                }.getOrNull()
            }
            if (texte == null) {
                _message.value = Avertissements.OPML_ILLISIBLE
                return@launch
            }
            importerOpml(texte)
        }
    }

    fun importerOpml(contenu: String) {
        viewModelScope.launch {
            _enCours.value = true
            val bilan = runCatching { depot.importOpml(contenu) }
            _enCours.value = false
            val resultat = bilan.getOrNull()
            _message.value = when {
                resultat == null -> Avertissements.OPML_ILLISIBLE
                resultat.lues == 0 -> Avertissements.OPML_SANS_SOURCE
                resultat.sources == 0 && resultat.dossiers == 0 ->
                    "Ces sources sont déjà dans l'application."
                else ->
                    "${resultat.sources} source(s) et ${resultat.dossiers} dossier(s) importés. Collecte en cours…"
            }
            if (resultat != null && resultat.sources > 0) rafraichir(silencieux = true)
        }
    }

    /** Message à l'utilisateur depuis un écran (affiché par la barre de message de l'écran principal). */
    fun signaler(texte: String) {
        _message.value = texte
    }

    fun exporterOpml(onPret: (String) -> Unit) {
        viewModelScope.launch { onPret(depot.exportOpml()) }
    }

    fun effacerMessage() {
        _message.value = null
    }

    // ----------------------------------------------------------- réglages

    // ------------------------------------------------------ sources en échec

    /** Nouvel essai immédiat sur une source en échec. */
    fun reessayerSource(feedId: Long) {
        viewModelScope.launch {
            runCatching { depot.refreshById(feedId) }
                .onSuccess { _message.value = "Source actualisée." }
                .onFailure { _message.value = "Échec : ${it.message ?: "collecte impossible"}" }
        }
    }

    /** Nouvel essai sur toutes les sources en échec, une par une. */
    fun reessayerToutesLesSources(feedIds: List<Long>) {
        if (feedIds.isEmpty()) return
        if (_enCours.value) return
        viewModelScope.launch {
            _enCours.value = true
            var reussies = 0
            for (id in feedIds) {
                runCatching { depot.refreshById(id) }.onSuccess { reussies++ }
            }
            _enCours.value = false
            _message.value = when (reussies) {
                feedIds.size -> "Toutes les sources répondent à nouveau."
                0 -> "Aucune source n'a répondu."
                else -> "${reussies} source(s) sur ${feedIds.size} répondent à nouveau."
            }
        }
    }

    // ------------------------------------------------------------- onglet IA

    /** Les modèles renvoient parfois du gras markdown : inutile à l'écran. */
    private fun nettoyerReponse(texte: String): String = texte
        .replace("**", "")
        .replace("__", "")
        .trim()

    /** Tâche du résumé en cours, pour pouvoir l'annuler. */
    private var tacheResume: Job? = null

    /** Résumé d'un article par le modèle local. */
    fun resumerArticle(articleId: Long) {
        if (_resumeEnCours.value != null) {
            _message.value = "Un résumé est déjà en cours : attends sa fin ou annule-le."
            return
        }
        _resumeEnCours.value = articleId
        tacheResume = viewModelScope.launch {
            val courant = reglages.value
            try {
                // Les listes ne portent que des extraits : le résumé lit l'article complet.
                val article = depot.article(articleId) ?: return@launch
                val texte = buildString {
                    append(article.title)
                    append("\n\n")
                    append(
                        article.textContent
                            ?: fr.fh.rss.data.net.FeedParser.texteSimple(
                                article.contentHtml ?: article.summaryHtml,
                            ),
                    )
                }
                val resume = ollama.demander(
                    base = courant.ollamaUrl,
                    modele = courant.ollamaModele,
                    consigne = fr.fh.rss.data.net.Ollama.CONSIGNE_RESUME,
                    contenu = texte,
                    nombreTokens = 400,
                )
                _resumes.value = _resumes.value + (article.id to nettoyerReponse(resume))
                _ollamaEtat.value = "Connecté · ${courant.ollamaModele}"
            } catch (annule: kotlinx.coroutines.CancellationException) {
                throw annule
            } catch (erreur: Exception) {
                val detail = fr.fh.rss.data.net.Ollama.messageErreur(courant.ollamaUrl, erreur)
                _ollamaEtat.value = detail
                _message.value = "Résumé impossible : $detail"
            } finally {
                // Un résumé annulé a déjà libéré l'état : ne pas écraser celui d'un autre article.
                if (_resumeEnCours.value == articleId) _resumeEnCours.value = null
            }
        }
    }

    /** Annule le résumé en cours : l'appel réseau est coupé, pas seulement masqué. */
    fun annulerResume() {
        if (_resumeEnCours.value == null) return
        tacheResume?.cancel()
        tacheResume = null
        _resumeEnCours.value = null
        _message.value = "Résumé annulé."
    }

    /** Synthèse des titres non lus par le modèle local. */
    fun syntheseNonLus() {
        if (_iaEnCours.value) return
        val nonLus = articles.value.filter { !it.isRead }.take(80)
        if (nonLus.isEmpty()) {
            _message.value = "Aucun article non lu à synthétiser."
            return
        }
        _iaEnCours.value = true
        _synthese.value = null
        viewModelScope.launch {
            val courant = reglages.value
            val contenu = nonLus.joinToString("\n") { "- ${it.title}" }
            try {
                val texte = ollama.demander(
                    base = courant.ollamaUrl,
                    modele = courant.ollamaModele,
                    consigne = fr.fh.rss.data.net.Ollama.CONSIGNE_SYNTHESE,
                    contenu = contenu,
                    nombreTokens = 600,
                )
                _synthese.value = nettoyerReponse(texte)
                _ollamaEtat.value = "Connecté · ${courant.ollamaModele}"
            } catch (annule: kotlinx.coroutines.CancellationException) {
                throw annule
            } catch (erreur: Exception) {
                val detail = fr.fh.rss.data.net.Ollama.messageErreur(courant.ollamaUrl, erreur)
                _ollamaEtat.value = detail
                _message.value = "Synthèse impossible : $detail"
            } finally {
                _iaEnCours.value = false
            }
        }
    }

    fun effacerSynthese() {
        _synthese.value = null
    }

    /**
     * Teste le serveur Ollama en silence (pas de message, pas de liste de
     * modèles) : seul l'indicateur de l'onglet IA change.
     */
    fun verifierIa() {
        viewModelScope.launch {
            val courant = reglages.value
            val disponible = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ollama.modeles(courant.ollamaUrl)
                }
            }.isSuccess
            _iaDisponible.value = disponible
        }
    }

    /**
     * Test de connexion au serveur Ollama et liste des modèles installés.
     * [adresse] : l'adresse à tester (celle qui vient d'être saisie) ; par
     * défaut, celle des réglages.
     */
    fun testerOllama(adresse: String = reglages.value.ollamaUrl) {
        viewModelScope.launch { testerAdresse(adresse) }
    }

    /**
     * « Enregistrer et tester » : l'adresse est enregistrée (normalisée), puis le
     * test part sur cette même valeur, sans attendre que les réglages se
     * rafraîchissent (sinon le test visait l'ancienne adresse).
     */
    fun enregistrerEtTesterOllama(saisie: String) {
        viewModelScope.launch {
            reglagesDepot.setOllamaUrl(saisie)
            testerAdresse(saisie)
        }
    }

    private suspend fun testerAdresse(adresse: String) {
        try {
            val liste = ollama.modeles(adresse)
            _modelesOllama.value = liste
            _iaDisponible.value = true
            _ollamaEtat.value = if (liste.isEmpty()) {
                "Serveur joignable, aucun modèle installé"
            } else {
                "Connecté · ${liste.size} modèle(s)"
            }
            _message.value = if (liste.isEmpty()) {
                "Serveur joignable, aucun modèle installé."
            } else {
                "Serveur joignable : ${liste.size} modèle(s)."
            }
        } catch (annule: kotlinx.coroutines.CancellationException) {
            throw annule
        } catch (erreur: Exception) {
            _modelesOllama.value = emptyList()
            _iaDisponible.value = false
            val detail = fr.fh.rss.data.net.Ollama.messageErreur(adresse, erreur)
            _ollamaEtat.value = detail
            _message.value = detail
        }
    }

    fun setNotifierNouveaux(actif: Boolean) =
        viewModelScope.launch { reglagesDepot.setNotifierNouveaux(actif) }

    fun setSilenceNuit(actif: Boolean) =
        viewModelScope.launch { reglagesDepot.setSilenceNuit(actif) }

    fun setOllamaUrl(valeur: String) = viewModelScope.launch { reglagesDepot.setOllamaUrl(valeur) }

    fun setOllamaModele(valeur: String) =
        viewModelScope.launch { reglagesDepot.setOllamaModele(valeur) }

    fun setTheme(valeur: String) = viewModelScope.launch { reglagesDepot.setTheme(valeur) }

    fun setVue(valeur: String) = viewModelScope.launch { reglagesDepot.setVue(valeur) }

    /** Taille du texte du lecteur (pour cent), ramenée dans la plage permise à l'écriture. */
    fun setTailleTexte(pourcent: Int) =
        viewModelScope.launch { reglagesDepot.setTailleTexte(pourcent) }

    fun setCollectePeriodique(actif: Boolean) {
        viewModelScope.launch {
            reglagesDepot.setCollectePeriodique(actif)
            val courant = reglages.value
            RefreshScheduler.programmer(
                getApplication(),
                actif,
                courant.intervalleMinutes,
                courant.wifiUniquement,
            )
        }
    }

    fun setIntervalle(minutes: Int) {
        viewModelScope.launch {
            reglagesDepot.setIntervalle(minutes)
            val courant = reglages.value
            if (courant.collectePeriodique) {
                RefreshScheduler.programmer(
                    getApplication(),
                    true,
                    minutes,
                    courant.wifiUniquement,
                )
            }
        }
    }

    fun setWifiUniquement(actif: Boolean) {
        viewModelScope.launch {
            reglagesDepot.setWifiUniquement(actif)
            val courant = reglages.value
            if (courant.collectePeriodique) {
                RefreshScheduler.programmer(
                    getApplication(),
                    true,
                    courant.intervalleMinutes,
                    actif,
                )
            }
        }
    }

    fun setArchiveMax(nombre: Int) = viewModelScope.launch { reglagesDepot.setArchiveMax(nombre) }
}

/**
 * Avancement d'une collecte : [faits] sources traitées sur [total] à traiter.
 * [total] vaut 0 tant que la liste des sources n'est pas connue.
 */
data class ProgresCollecte(val faits: Int, val total: Int) {
    /** Fraction accomplie, entre 0 et 1. */
    val fraction: Float get() = if (total <= 0) 0f else (faits.toFloat() / total).coerceIn(0f, 1f)
}
