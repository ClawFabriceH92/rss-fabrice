package fr.fh.rss.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import fr.fh.rss.data.db.FolderEntity
import fr.fh.rss.update.UpdateManager

@Composable
fun DialogueAjoutFlux(
    dossiers: List<FolderEntity>,
    onValider: (String, Long?) -> Unit,
    onFermer: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var dossierId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text("Ajouter un flux") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Adresse du flux ou du site") },
                    placeholder = { Text("https://exemple.fr") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Une adresse de site suffit : le flux est détecté automatiquement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (dossiers.isNotEmpty()) {
                    Text(
                        "Dossier",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    LigneChoix("Aucun dossier", dossierId == null) { dossierId = null }
                    for (dossier in dossiers) {
                        LigneChoix(dossier.name, dossierId == dossier.id) { dossierId = dossier.id }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onValider(url, dossierId) }, enabled = url.isNotBlank()) {
                Text("Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

@Composable
fun DialogueNomDossier(
    titre: String,
    valeurInitiale: String,
    onValider: (String) -> Unit,
    onFermer: () -> Unit,
) {
    var nom by remember { mutableStateOf(valeurInitiale) }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(titre) },
        text = {
            OutlinedTextField(
                value = nom,
                onValueChange = { nom = it },
                label = { Text("Nom du dossier") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onValider(nom) }, enabled = nom.isNotBlank()) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

@Composable
fun DialogueChoixDossier(
    titre: String,
    dossiers: List<FolderEntity>,
    onValider: (Long?) -> Unit,
    onFermer: () -> Unit,
) {
    var choix by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onFermer,
        title = { Text(titre) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LigneChoix("Aucun dossier", choix == null) { choix = null }
                for (dossier in dossiers) {
                    LigneChoix(dossier.name, choix == dossier.id) { choix = dossier.id }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onValider(choix) }) { Text("Déplacer") } },
        dismissButton = { TextButton(onClick = onFermer) { Text("Annuler") } },
    )
}

@Composable
private fun LigneChoix(libelle: String, selectionne: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selectionne, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selectionne, onClick = onClick)
        Text(libelle, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun DialogueMiseAJour(
    etat: UpdateManager.State,
    onTelecharger: () -> Unit,
    onInstaller: () -> Unit,
    onAutoriser: () -> Unit,
    onFermer: () -> Unit,
) {
    when (etat) {
        is UpdateManager.State.Available -> AlertDialog(
            onDismissRequest = onFermer,
            title = { Text("Version ${etat.version} disponible") },
            text = { Text(etat.notes.ifBlank { "Une nouvelle version est prête." }) },
            confirmButton = { TextButton(onClick = onTelecharger) { Text("Télécharger") } },
            dismissButton = { TextButton(onClick = onFermer) { Text("Plus tard") } },
        )

        is UpdateManager.State.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Téléchargement… ${(etat.progress * 100).toInt()} %") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { etat.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {},
        )

        is UpdateManager.State.ReadyToInstall -> AlertDialog(
            onDismissRequest = onFermer,
            title = { Text("Prêt à installer") },
            text = { Text("La version ${etat.version} est téléchargée.") },
            confirmButton = { TextButton(onClick = onInstaller) { Text("Installer") } },
            dismissButton = { TextButton(onClick = onFermer) { Text("Plus tard") } },
        )

        is UpdateManager.State.UpToDate -> AlertDialog(
            onDismissRequest = onFermer,
            title = { Text("À jour") },
            text = { Text("Version installée : ${etat.version}") },
            confirmButton = { TextButton(onClick = onFermer) { Text("OK") } },
        )

        is UpdateManager.State.Failed -> AlertDialog(
            onDismissRequest = onFermer,
            title = { Text("Mise à jour impossible") },
            text = { Text(etat.detail ?: "GitHub n'a pas répondu.") },
            confirmButton = { TextButton(onClick = onFermer) { Text("OK") } },
            dismissButton = {
                TextButton(onClick = onAutoriser) { Text("Autoriser l'installation") }
            },
        )

        UpdateManager.State.Idle -> Unit
    }
}
