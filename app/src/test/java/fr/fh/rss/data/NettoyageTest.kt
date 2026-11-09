package fr.fh.rss.data

import fr.fh.rss.data.db.FeedEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Propositions de nettoyage : une source n'est proposée au retrait que pour une
 * raison mesurable, et le silence d'un an compte autant qu'un échec, parce que
 * l'onglet « Flux en erreur » ne voit pas un flux qui répond encore.
 */
class NettoyageTest {

    private val jour = TimeUnit.DAYS.toMillis(1)
    private val maintenant = 1_800_000_000_000L

    private fun source(
        id: Long,
        titre: String = "Source $id",
        erreur: String? = null,
        errorSince: Long? = null,
        ajouteIlYA: Long = 400,
    ) = FeedEntity(
        id = id,
        url = "https://exemple.fr/$id.xml",
        title = titre,
        lastError = erreur,
        errorSince = errorSince,
        errorCount = if (erreur == null) 0 else 3,
        addedAt = maintenant - ajouteIlYA * jour,
    )

    @Test
    fun `une source en echec depuis longtemps est proposee`() {
        val s = source(1, erreur = "HTTP 404", errorSince = maintenant - 40 * jour)
        val propositions = Nettoyage.propositions(listOf(s), emptyMap(), maintenant)
        assertEquals(1, propositions.size)
        assertEquals(Nettoyage.Raison.EN_ERREUR, propositions.single().raison)
        assertEquals(40L, propositions.single().retardJours)
        assertTrue(propositions.single().detail, propositions.single().detail.contains("HTTP 404"))
    }

    @Test
    fun `une panne recente n'est pas une raison de supprimer`() {
        val s = source(1, erreur = "HTTP 500", errorSince = maintenant - 3 * jour)
        // La source publiait encore la semaine dernière : seule la panne est récente.
        assertTrue(Nettoyage.propositions(listOf(s), mapOf(1L to maintenant - 7 * jour), maintenant).isEmpty())
    }

    @Test
    fun `une source qui repond mais ne publie plus depuis un an est proposee`() {
        val s = source(1)
        val dernier = mapOf(1L to maintenant - 400 * jour)
        val propositions = Nettoyage.propositions(listOf(s), dernier, maintenant)
        assertEquals(Nettoyage.Raison.MUETTE, propositions.single().raison)
        assertEquals(400L, propositions.single().retardJours)
    }

    @Test
    fun `une source qui publie encore n'est pas touchee`() {
        val s = source(1)
        assertTrue(Nettoyage.propositions(listOf(s), mapOf(1L to maintenant - 2 * jour), maintenant).isEmpty())
    }

    @Test
    fun `une source sans aucun article depuis son ajout est proposee`() {
        val s = source(1, ajouteIlYA = 60)
        val propositions = Nettoyage.propositions(listOf(s), emptyMap(), maintenant)
        assertEquals(Nettoyage.Raison.JAMAIS_D_ARTICLE, propositions.single().raison)
        assertEquals(60L, propositions.single().retardJours)
    }

    @Test
    fun `une source ajoutee recemment sans article n'est pas proposee`() {
        val s = source(1, ajouteIlYA = 5)
        assertTrue(Nettoyage.propositions(listOf(s), emptyMap(), maintenant).isEmpty())
    }

    @Test
    fun `l'echec prime sur le silence et les propositions sont triees`() {
        val enEchec = source(1, erreur = "HTTP 404", errorSince = maintenant - 600 * jour)
        val muette = source(2)
        val jamais = source(3, ajouteIlYA = 45)
        val propositions = Nettoyage.propositions(
            listOf(muette, jamais, enEchec),
            mapOf(2L to maintenant - 500 * jour),
            maintenant,
        )
        assertEquals(listOf(1L, 2L, 3L), propositions.map { it.id })
        assertEquals(Nettoyage.Raison.EN_ERREUR, propositions[0].raison)
        assertEquals(Nettoyage.Raison.MUETTE, propositions[1].raison)
        assertEquals(Nettoyage.Raison.JAMAIS_D_ARTICLE, propositions[2].raison)
    }

    @Test
    fun `le bilan compte chaque famille`() {
        val propositions = Nettoyage.propositions(
            listOf(
                source(1, erreur = "HTTP 404", errorSince = maintenant - 40 * jour),
                source(2),
                source(3, ajouteIlYA = 60),
            ),
            mapOf(2L to maintenant - 500 * jour),
            maintenant,
        )
        val resume = Nettoyage.resume(propositions)
        assertTrue(resume, resume.contains("1 en échec"))
        assertTrue(resume, resume.contains("1 sans article depuis plus d'un an"))
        assertTrue(resume, resume.contains("1 sans aucun article"))
        assertTrue(Nettoyage.resume(emptyList()).contains("Aucune source à retirer"))
    }

    @Test
    fun `un seuil plus large ecarte les cas limites`() {
        val s = source(1)
        val dernier = mapOf(1L to maintenant - 200 * jour)
        // 200 jours de silence : proposée avec un seuil de 180, épargnée avec 365 puis 730.
        assertTrue(Nettoyage.propositions(listOf(s), dernier, maintenant, seuilMuetteJours = 180).isNotEmpty())
        assertTrue(Nettoyage.propositions(listOf(s), dernier, maintenant).isEmpty())
        assertTrue(
            Nettoyage.propositions(listOf(s), dernier, maintenant, seuilMuetteJours = 730).isEmpty(),
        )
    }
}
