package fr.fh.rss.data

import java.io.File

/**
 * Fichiers d'export OPML écrits dans `files/exports` avant le partage.
 *
 * Chaque export crée un nouveau fichier horodaté : sans purge, ils
 * s'accumulent indéfiniment dans le stockage privé de l'application.
 */
object ExportsOpml {

    /** Nombre d'exports conservés : celui qui vient d'être partagé (l'autre application le lit encore) et le précédent. */
    const val CONSERVES = 2

    private const val PREFIXE = "sources-rss-"
    private const val SUFFIXE = ".opml"

    /** Nom de fichier d'un export, daté de [horodatage] (ex. `20261108-0930`). */
    fun nomFichier(horodatage: String): String = "$PREFIXE$horodatage$SUFFIXE"

    fun estUnExport(fichier: File): Boolean =
        fichier.isFile && fichier.name.startsWith(PREFIXE) && fichier.name.endsWith(SUFFIXE)

    /**
     * Supprime les exports les plus anciens de [dossier] et garde les
     * [conserves] plus récents. Seuls les fichiers `sources-rss-*.opml` sont
     * touchés. Renvoie le nombre de fichiers supprimés ; une erreur sur un
     * fichier n'empêche pas de traiter les autres.
     */
    fun purger(dossier: File, conserves: Int = CONSERVES): Int {
        val exports = dossier.listFiles()?.filter { estUnExport(it) } ?: return 0
        // Plus récent d'abord ; à date égale, le nom (horodaté) départage.
        val tries = exports.sortedWith(
            compareByDescending<File> { it.lastModified() }.thenByDescending { it.name },
        )
        var supprimes = 0
        for (fichier in tries.drop(conserves.coerceAtLeast(0))) {
            if (runCatching { fichier.delete() }.getOrDefault(false)) supprimes++
        }
        return supprimes
    }
}
