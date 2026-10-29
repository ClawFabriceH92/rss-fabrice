package fr.fh.rss.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.fh.rss.data.Filtre
import fr.fh.rss.data.Reglages
import fr.fh.rss.data.db.ArticleEntity
import fr.fh.rss.data.db.FeedEntity
import fr.fh.rss.data.db.FolderEntity
import kotlinx.coroutines.launch

/** Écrans de l'application (pas de bibliothèque de navigation : trois états suffisent). */
sealed interface Ecran {
    data object Liste : Ecran
    data class Lecteur(val articleId: Long) : Ecran
    data object Reglages : Ecran
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    vm: MainViewModel,
    lienExterne: String?,
    onLienUtilise: () -> Unit,
) {
    val contexte = LocalContext.current
    var ecran by remember { mutableStateOf<Ecran>(Ecran.Liste) }
    var rechercheOuverte by remember { mutableStateOf(false) }
    var texteRecherche by remember { mutableStateOf("") }
    var dialogueAjout by remember { mutableStateOf(false) }
    var dialogueDossier by remember { mutableStateOf(false) }
    var sourceADeplacer by remember { mutableStateOf<FeedEntity?>(null) }

    val tiroir = rememberDrawerState(DrawerValue.Closed)
    val portee = rememberCoroutineScope()

    val articles by vm.articles.collectAsState()
    val selection by vm.selection.collectAsState()
    val sources by vm.sources.collectAsState()
    val dossiers by vm.dossiers.collectAsState()
    val nonLusSource by vm.nonLusParSource.collectAsState()
    val nonLusDossier by vm.nonLusParDossier.collectAsState()
    val nonLusTotal by vm.nonLusTotal.collectAsState()
    val enCours by vm.enCours.collectAsState()
    val message by vm.message.collectAsState()
    val reglages by vm.reglages.collectAsState()
    val majEtat by fr.fh.rss.update.UpdateManager.state.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val titres = remember(sources) { sources.associate { it.id to it.title } }

    LaunchedEffect(Unit) { vm.rafraichirAuLancement() }

    LaunchedEffect(lienExterne) {
        if (!lienExterne.isNullOrBlank()) {
            vm.ajouterFlux(lienExterne, null)
            onLienUtilise()
        }
    }

    LaunchedEffect(message) {
        val texte = message
        if (texte != null) {
            snackbar.showSnackbar(texte, withDismissAction = true, duration = SnackbarDuration.Short)
            vm.effacerMessage()
        }
    }

    BackHandler(enabled = ecran != Ecran.Liste || rechercheOuverte) {
        if (rechercheOuverte) {
            rechercheOuverte = false
            texteRecherche = ""
            vm.setRecherche("")
        } else {
            ecran = Ecran.Liste
        }
    }

    ModalNavigationDrawer(
        drawerState = tiroir,
        drawerContent = {
            ModalDrawerSheet {
                ContenuTiroir(
                    selection = selection,
                    sources = sources,
                    dossiers = dossiers,
                    nonLusSource = nonLusSource,
                    nonLusDossier = nonLusDossier,
                    nonLusTotal = nonLusTotal,
                    onChoisir = { choix ->
                        vm.changerSelection(choix)
                        ecran = Ecran.Liste
                        portee.launch { tiroir.close() }
                    },
                    onAjouterFlux = { dialogueAjout = true },
                    onNouveauDossier = { dialogueDossier = true },
                    onDeplacer = { sourceADeplacer = it },
                    onMarquerLu = { vm.marquerSourceLue(it.id) },
                    onSupprimer = { vm.supprimerFlux(it.id) },
                    onMarquerToutLu = { vm.marquerToutLu() },
                    onReglages = {
                        ecran = Ecran.Reglages
                        portee.launch { tiroir.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (rechercheOuverte) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = texteRecherche,
                                onValueChange = {
                                    texteRecherche = it
                                    vm.setRecherche(it)
                                },
                                placeholder = { Text("Rechercher dans les articles") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    rechercheOuverte = false
                                    texteRecherche = ""
                                    vm.setRecherche("")
                                },
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer")
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                if (ecran is Ecran.Reglages) "Réglages" else selection.titre,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            if (ecran == Ecran.Liste) {
                                IconButton(onClick = { portee.launch { tiroir.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            } else {
                                IconButton(onClick = { ecran = Ecran.Liste }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                                }
                            }
                        },
                        actions = {
                            if (ecran == Ecran.Liste) {
                                IconButton(onClick = { rechercheOuverte = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Rechercher")
                                }
                                IconButton(onClick = { vm.rafraichir() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                                }
                                IconButton(onClick = { ecran = Ecran.Reglages }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Réglages")
                                }
                            }
                        },
                    )
                }
            },
            floatingActionButton = {
                if (ecran == Ecran.Liste) {
                    ExtendedFloatingActionButton(
                        onClick = { dialogueAjout = true },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Ajouter un flux") },
                    )
                }
            },
        ) { marges ->
            Box(Modifier.padding(marges)) {
                when (val courant = ecran) {
                    Ecran.Liste -> ZoneArticles(
                        articles = articles,
                        titres = titres,
                        vue = reglages.vue,
                        enCours = enCours,
                        onRafraichir = { vm.rafraichir() },
                        onOuvrir = { ecran = Ecran.Lecteur(it.id) },
                        onBasculerLu = { vm.basculerLu(it) },
                        onFavori = { vm.basculerFavori(it) },
                    )

                    is Ecran.Lecteur -> EcranLecteur(
                        vm = vm,
                        articleId = courant.articleId,
                        onRetour = { ecran = Ecran.Liste },
                    )

                    Ecran.Reglages -> EcranReglages(
                        vm = vm,
                        reglages = reglages,
                        nombreSources = sources.size,
                        onRetour = { ecran = Ecran.Liste },
                    )
                }
            }
        }
    }

    if (dialogueAjout) {
        DialogueAjoutFlux(
            dossiers = dossiers,
            onValider = { url, dossierId ->
                vm.ajouterFlux(url, dossierId)
                dialogueAjout = false
            },
            onFermer = { dialogueAjout = false },
        )
    }

    if (dialogueDossier) {
        DialogueNomDossier(
            titre = "Nouveau dossier",
            valeurInitiale = "",
            onValider = { nom ->
                vm.creerDossier(nom)
                dialogueDossier = false
            },
            onFermer = { dialogueDossier = false },
        )
    }

    sourceADeplacer?.let { feed ->
        DialogueChoixDossier(
            titre = "Déplacer « ${feed.title} »",
            dossiers = dossiers,
            onValider = { dossierId ->
                vm.deplacerFlux(feed.id, dossierId)
                sourceADeplacer = null
            },
            onFermer = { sourceADeplacer = null },
        )
    }

    DialogueMiseAJour(
        etat = majEtat,
        onTelecharger = { fr.fh.rss.update.UpdateManager.download(contexte) },
        onInstaller = { fr.fh.rss.update.UpdateManager.install(contexte) },
        onAutoriser = { fr.fh.rss.update.UpdateManager.openInstallSettings(contexte) },
        onFermer = { fr.fh.rss.update.UpdateManager.dismiss() },
    )
}

// ------------------------------------------------------------------ tiroir

@Composable
private fun ContenuTiroir(
    selection: Selection,
    sources: List<FeedEntity>,
    dossiers: List<FolderEntity>,
    nonLusSource: Map<Long, Int>,
    nonLusDossier: Map<Long, Int>,
    nonLusTotal: Int,
    onChoisir: (Selection) -> Unit,
    onAjouterFlux: () -> Unit,
    onNouveauDossier: () -> Unit,
    onDeplacer: (FeedEntity) -> Unit,
    onMarquerLu: (FeedEntity) -> Unit,
    onSupprimer: (FeedEntity) -> Unit,
    onMarquerToutLu: () -> Unit,
    onReglages: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.RssFeed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "RSS by Fabrice Heuvrard",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                NavigationDrawerItem(
                    label = { Text("Tous les articles") },
                    badge = if (nonLusTotal > 0) {
                        { Text(nonLusTotal.toString()) }
                    } else {
                        null
                    },
                    selected = selection.filtre == Filtre.Tous,
                    onClick = { onChoisir(Selection(Filtre.Tous, "Tous les articles")) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Non lus") },
                    selected = selection.filtre == Filtre.NonLus,
                    onClick = { onChoisir(Selection(Filtre.NonLus, "Non lus")) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Lire plus tard") },
                    selected = selection.filtre == Filtre.Favoris,
                    onClick = { onChoisir(Selection(Filtre.Favoris, "Lire plus tard")) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Dossiers et sources",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onNouveauDossier) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Nouveau dossier")
                    }
                    IconButton(onClick = onAjouterFlux) {
                        Icon(Icons.Default.Add, contentDescription = "Ajouter un flux")
                    }
                }
            }

            val sansDossier = sources.filter { it.folderId == null }
            for (dossier in dossiers) {
                item(key = "dossier-${dossier.id}") {
                    NavigationDrawerItem(
                        label = { Text(dossier.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        badge = (nonLusDossier[dossier.id] ?: 0).takeIf { it > 0 }?.let {
                            { Text(it.toString()) }
                        },
                        selected = (selection.filtre as? Filtre.Dossier)?.id == dossier.id,
                        onClick = {
                            onChoisir(Selection(Filtre.Dossier(dossier.id), dossier.name))
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
                val dedans = sources.filter { it.folderId == dossier.id }
                items(dedans, key = { "flux-${it.id}" }) { source ->
                    LigneSource(
                        source = source,
                        nonLus = nonLusSource[source.id] ?: 0,
                        selectionnee = (selection.filtre as? Filtre.Flux)?.id == source.id,
                        indente = true,
                        onClick = { onChoisir(Selection(Filtre.Flux(source.id), source.title)) },
                        onDeplacer = { onDeplacer(source) },
                        onMarquerLu = { onMarquerLu(source) },
                        onSupprimer = { onSupprimer(source) },
                    )
                }
            }

            if (sansDossier.isNotEmpty()) {
                item {
                    Text(
                        "Sans dossier",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                items(sansDossier, key = { "flib-${it.id}" }) { source ->
                    LigneSource(
                        source = source,
                        nonLus = nonLusSource[source.id] ?: 0,
                        selectionnee = (selection.filtre as? Filtre.Flux)?.id == source.id,
                        indente = false,
                        onClick = { onChoisir(Selection(Filtre.Flux(source.id), source.title)) },
                        onDeplacer = { onDeplacer(source) },
                        onMarquerLu = { onMarquerLu(source) },
                        onSupprimer = { onSupprimer(source) },
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                NavigationDrawerItem(
                    label = { Text("Tout marquer comme lu") },
                    icon = { Icon(Icons.Default.Done, contentDescription = null) },
                    selected = false,
                    onClick = onMarquerToutLu,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            item {
                NavigationDrawerItem(
                    label = { Text("Réglages et OPML") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    selected = false,
                    onClick = onReglages,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LigneSource(
    source: FeedEntity,
    nonLus: Int,
    selectionnee: Boolean,
    indente: Boolean,
    onClick: () -> Unit,
    onDeplacer: () -> Unit,
    onMarquerLu: () -> Unit,
    onSupprimer: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (indente) 40.dp else 24.dp,
                end = 8.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selectionnee) Icons.Default.FolderOpen else Icons.Default.RssFeed,
            contentDescription = null,
            tint = if (selectionnee) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            source.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (nonLus > 0) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (nonLus > 0) {
            Text(
                nonLus.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Actions sur la source",
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Marquer comme lu") },
                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = {
                        menu = false
                        onMarquerLu()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Déplacer dans un dossier") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = {
                        menu = false
                        onDeplacer()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Supprimer la source") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menu = false
                        onSupprimer()
                    },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ liste

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneArticles(
    articles: List<ArticleEntity>,
    titres: Map<Long, String>,
    vue: String,
    enCours: Boolean,
    onRafraichir: () -> Unit,
    onOuvrir: (ArticleEntity) -> Unit,
    onBasculerLu: (ArticleEntity) -> Unit,
    onFavori: (ArticleEntity) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = enCours,
        onRefresh = onRafraichir,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (articles.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.RssFeed,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (enCours) "Collecte en cours…" else "Aucun article",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ajoutez un flux, importez un OPML ou tirez vers le bas pour actualiser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                when (vue) {
                    "cartes" -> items(articles, key = { it.id }) { article ->
                        CarteArticle(
                            article = article,
                            source = titres[article.feedId].orEmpty(),
                            grand = false,
                            withImage = true,
                            onOuvrir = { onOuvrir(article) },
                            onFavori = { onFavori(article) },
                        )
                    }

                    "magazine" -> {
                        item(key = "une") {
                            CarteArticle(
                                article = articles.first(),
                                source = titres[articles.first().feedId].orEmpty(),
                                grand = true,
                                withImage = true,
                                onOuvrir = { onOuvrir(articles.first()) },
                                onFavori = { onFavori(articles.first()) },
                            )
                        }
                        items(articles.drop(1), key = { it.id }) { article ->
                            LigneBalayable(
                                article = article,
                                source = titres[article.feedId].orEmpty(),
                                withImage = true,
                                onOuvrir = { onOuvrir(article) },
                                onBasculerLu = { onBasculerLu(article) },
                                onFavori = { onFavori(article) },
                            )
                        }
                    }

                    else -> items(articles, key = { it.id }) { article ->
                        LigneBalayable(
                            article = article,
                            source = titres[article.feedId].orEmpty(),
                            withImage = false,
                            onOuvrir = { onOuvrir(article) },
                            onBasculerLu = { onBasculerLu(article) },
                            onFavori = { onFavori(article) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LigneBalayable(
    article: ArticleEntity,
    source: String,
    withImage: Boolean,
    onOuvrir: () -> Unit,
    onBasculerLu: () -> Unit,
    onFavori: () -> Unit,
) {
    val etat = rememberSwipeToDismissBoxState(
        confirmValueChange = { valeur ->
            if (valeur == SwipeToDismissBoxValue.StartToEnd) onBasculerLu()
            false
        },
    )
    SwipeToDismissBox(
        state = etat,
        enableDismissFromEndToStart = false,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    if (article.isRead) Icons.Default.MarkEmailUnread else Icons.Default.Done,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) {
        LigneArticle(article, source, withImage, onOuvrir, onFavori)
    }
}

@Composable
private fun LigneArticle(
    article: ArticleEntity,
    source: String,
    withImage: Boolean,
    onOuvrir: () -> Unit,
    onFavori: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOuvrir)
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (withImage && !article.imageUrl.isNullOrBlank()) {
            Vignette(article.imageUrl, 64)
            Spacer(Modifier.width(12.dp))
        }
        Box(
            Modifier
                .padding(top = 7.dp, end = 10.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (article.isRead) Color.Transparent else MaterialTheme.colorScheme.primary,
                ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                article.title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.SemiBold,
            )
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "  ·  ${dateCourte(article.publishedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onFavori) {
            Icon(
                if (article.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Lire plus tard",
                tint = if (article.isStarred) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CarteArticle(
    article: ArticleEntity,
    source: String,
    grand: Boolean,
    withImage: Boolean,
    onOuvrir: () -> Unit,
    onFavori: () -> Unit,
) {
    Card(
        onClick = onOuvrir,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (article.isRead) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        if (withImage && !article.imageUrl.isNullOrBlank()) {
            Vignette(article.imageUrl, if (grand) 200 else 150, pleineLargeur = true)
        }
        Column(Modifier.padding(14.dp)) {
            Text(
                article.title,
                style = if (grand) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.titleMedium,
                fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = if (grand) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$source  ·  ${dateCourte(article.publishedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onFavori, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (article.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Lire plus tard",
                        tint = if (article.isStarred) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Vignette(url: String?, hauteur: Int, pleineLargeur: Boolean = false) {
    coil.compose.AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = Modifier
            .then(if (pleineLargeur) Modifier.fillMaxWidth() else Modifier.width(64.dp))
            .height(hauteur.dp)
            .clip(RoundedCornerShape(if (pleineLargeur) 12.dp else 8.dp)),
    )
}
