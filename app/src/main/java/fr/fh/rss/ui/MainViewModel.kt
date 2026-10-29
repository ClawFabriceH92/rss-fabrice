package fr.fh.rss.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.fh.rss.RssApp
import fr.fh.rss.data.Filtre
import fr.fh.rss.data.Reglages
import fr.fh.rss.data.db.ArticleEntity
import fr.fh.rss.data.db.FeedEntity
import fr.fh.rss.data.db.FolderEntity
import fr.fh.rss.data.model.AddResult
import fr.fh.rss.data.model.FeedUnread
import fr.fh.rss.data.model.FolderUnread
import fr.fh.rss.data.model.RefreshSummary
import fr.fh.rss.work.RefreshScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Ce que l'écran principal affiche. */
data class Selection(val filtre: Filtre, val titre: String)

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

    val nonLusParDossier: StateFlow<Map<Long, Int>> = depot.observeUnreadByFolder()
        .map { liste: List<FolderUnread> -> liste.associate { it.folderId to it.nombre } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val nonLusTotal: StateFlow<Int> = depot.observeUnreadTotal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _selection = MutableStateFlow(Selection(Filtre.Tous, "Tous les articles"))
    val selection: StateFlow<Selection> = _selection.asStateFlow()

    private val _recherche = MutableStateFlow("")
    val recherche: StateFlow<String> = _recherche.asStateFlow()

    val articles: StateFlow<List<ArticleEntity>> =
        combine(_selection, _recherche) { sel, requete -> sel to requete }
            .flatMapLatest { (sel, requete) ->
                if (requete.isBlank()) depot.observeArticles(sel.filtre)
                else depot.observeSearch(requete)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _enCours = MutableStateFlow(false)
    val enCours: StateFlow<Boolean> = _enCours.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var collecteFaite = false

    // ------------------------------------------------------------ actions

    fun changerSelection(sel: Selection) {
        _selection.value = sel
        _recherche.value = ""
    }

    fun setRecherche(texte: String) {
        _recherche.value = texte
    }

    /** Collecte systématique au lancement, puis à la demande. */
    fun rafraichir(silencieux: Boolean = false) {
        if (_enCours.value) return
        viewModelScope.launch {
            _enCours.value = true
            val bilan = runCatching { depot.refreshAll() }
                .getOrElse { RefreshSummary(echecs = listOf("collecte" to (it.message ?: "erreur")) ) }
            _enCours.value = false
            val texte = when {
                bilan.sources == 0 -> "Aucune source : ajoutez un flux ou importez un OPML."
                bilan.echecs.isEmpty() && bilan.nouveaux == 0 -> "Aucun nouvel article."
                bilan.echecs.isEmpty() -> "${bilan.nouveaux} nouvel(aux) article(s)."
                else -> "${bilan.nouveaux} nouvel(aux) article(s), ${bilan.echecs.size} source(s) en échec : " +
                    bilan.echecs.take(2).joinToString(", ") { "${it.first} (${it.second})" }
            }
            _message.value = texte
        }
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
            if (_selection.value.filtre !is Filtre.Tous) {
                _selection.value = Selection(Filtre.Tous, "Tous les articles")
            }
        }
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
                _selection.value = Selection(Filtre.Tous, "Tous les articles")
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

    fun supprimerFlux(feedId: Long) {
        viewModelScope.launch {
            depot.deleteFeed(feedId)
            if ((_selection.value.filtre as? Filtre.Flux)?.id == feedId) {
                _selection.value = Selection(Filtre.Tous, "Tous les articles")
            }
            _message.value = "Source supprimée."
        }
    }

    fun marquerSourceLue(feedId: Long) {
        viewModelScope.launch { depot.markFeedRead(feedId) }
    }

    fun marquerToutLu() {
        viewModelScope.launch {
            depot.markAllRead()
            _message.value = "Tous les articles sont marqués comme lus."
        }
    }

    fun basculerLu(article: ArticleEntity) {
        viewModelScope.launch { depot.setRead(article.id, !article.isRead) }
    }

    fun marquerLu(articleId: Long) {
        viewModelScope.launch { depot.setRead(articleId, true) }
    }

    fun basculerFavori(article: ArticleEntity) {
        viewModelScope.launch { depot.setStarred(article.id, !article.isStarred) }
    }

    fun basculerFavori(articleId: Long, favori: Boolean) {
        viewModelScope.launch { depot.setStarred(articleId, favori) }
    }

    fun observerArticle(id: Long): Flow<ArticleEntity?> = depot.observeArticle(id)

    /** Télécharge le texte complet pour la lecture hors connexion. */
    fun completerArticle(articleId: Long) {
        viewModelScope.launch { depot.fetchFullContent(articleId) }
    }

    fun importerOpml(contenu: String) {
        viewModelScope.launch {
            _enCours.value = true
            val bilan = runCatching { depot.importOpml(contenu) }
            _enCours.value = false
            val resultat = bilan.getOrNull()
            _message.value = if (resultat == null) {
                "Import impossible : fichier OPML illisible."
            } else {
                "${resultat.sources} source(s) et ${resultat.dossiers} dossier(s) importés. Collecte en cours…"
            }
            if (resultat != null && resultat.sources > 0) rafraichir(silencieux = true)
        }
    }

    fun exporterOpml(onPret: (String) -> Unit) {
        viewModelScope.launch { onPret(depot.exportOpml()) }
    }

    fun effacerMessage() {
        _message.value = null
    }

    // ----------------------------------------------------------- réglages

    fun setTheme(valeur: String) = viewModelScope.launch { reglagesDepot.setTheme(valeur) }

    fun setVue(valeur: String) = viewModelScope.launch { reglagesDepot.setVue(valeur) }

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
