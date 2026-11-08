package fr.fh.rss.work

import fr.fh.rss.work.ReportNotification.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Report des notifications de nuit : rien n'est perdu, tout est annoncé au matin. */
class ReportNotificationTest {

    private fun decider(
        heure: Int,
        enAttente: Int,
        notifier: Boolean = true,
        silence: Boolean = true,
        permission: Boolean = true,
    ) = ReportNotification.decider(notifier, silence, heure, permission, enAttente)

    @Test
    fun `la nuit court de 22 h a 7 h`() {
        assertTrue(ReportNotification.heureSilencieuse(22))
        assertTrue(ReportNotification.heureSilencieuse(23))
        assertTrue(ReportNotification.heureSilencieuse(0))
        assertTrue(ReportNotification.heureSilencieuse(6))
        assertFalse(ReportNotification.heureSilencieuse(7))
        assertFalse(ReportNotification.heureSilencieuse(21))
    }

    @Test
    fun `la nuit les articles sont reportes et le repere ne bouge pas`() {
        val decision = decider(heure = 3, enAttente = 20)
        assertEquals(Decision.REPORTER, decision)
        assertEquals(1_000L, ReportNotification.nouveauRepere(decision, repere = 1_000L, maintenant = 9_000L))
    }

    @Test
    fun `au matin la notification annonce tout ce qui s'est accumule`() {
        // 3 h : 20 articles, reportés. 7 h : 2 de plus, le repère n'a pas bougé donc 22 en attente.
        val nuit = decider(heure = 3, enAttente = 20)
        assertEquals(Decision.REPORTER, nuit)
        val repere = ReportNotification.nouveauRepere(nuit, 1_000L, 5_000L)

        val matin = decider(heure = 7, enAttente = 22)
        assertEquals(Decision.NOTIFIER, matin)
        assertEquals(9_000L, ReportNotification.nouveauRepere(matin, repere, 9_000L))
    }

    @Test
    fun `sans article en attente rien n'est publie et le repere reste`() {
        val decision = decider(heure = 12, enAttente = 0)
        assertEquals(Decision.RIEN, decision)
        assertEquals(1_000L, ReportNotification.nouveauRepere(decision, 1_000L, 9_000L))
    }

    @Test
    fun `en journee la notification part`() {
        assertEquals(Decision.NOTIFIER, decider(heure = 14, enAttente = 1))
    }

    @Test
    fun `sans silence de nuit on notifie meme a trois heures`() {
        assertEquals(Decision.NOTIFIER, decider(heure = 3, enAttente = 5, silence = false))
    }

    @Test
    fun `notifications coupees ou permission refusee, le repere avance sans publier`() {
        val coupe = decider(heure = 12, enAttente = 5, notifier = false)
        val refusee = decider(heure = 12, enAttente = 5, permission = false)
        assertEquals(Decision.ABANDONNER, coupe)
        assertEquals(Decision.ABANDONNER, refusee)
        assertEquals(9_000L, ReportNotification.nouveauRepere(coupe, 1_000L, 9_000L))
    }
}
