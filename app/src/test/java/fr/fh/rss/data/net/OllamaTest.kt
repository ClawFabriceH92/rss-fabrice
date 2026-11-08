package fr.fh.rss.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Adresse d'Ollama et messages d'erreur lisibles. */
class OllamaTest {

    @Test
    fun `le schema manquant est ajoute`() {
        assertEquals("http://192.168.0.162:11434", Ollama.normaliser("192.168.0.162:11434"))
        assertEquals("http://ia162:11434", Ollama.normaliser("  ia162:11434/  "))
    }

    @Test
    fun `un schema present est conserve`() {
        assertEquals("http://192.168.0.162:11434", Ollama.normaliser("http://192.168.0.162:11434/"))
        assertEquals("https://ollama.exemple.fr", Ollama.normaliser("https://ollama.exemple.fr"))
    }

    @Test
    fun `une saisie vide donne l'adresse par defaut`() {
        assertEquals(Ollama.ADRESSE_DEFAUT, Ollama.normaliser("   "))
    }

    @Test
    fun `l'adresse normalisee est acceptee par OkHttp`() {
        assertTrue(Ollama.adresseValide("192.168.0.162:11434"))
        assertTrue(Ollama.adresseValide("http://localhost:11434"))
        assertFalse(Ollama.adresseValide("http://"))
        assertFalse(Ollama.adresseValide("http://adresse avec espaces"))
    }

    @Test
    fun `un serveur injoignable donne un message actionnable`() {
        val attendu = "Ollama injoignable sur 192.168.0.162 : es-tu sur le Wi-Fi de la maison ?"
        val base = "192.168.0.162:11434"
        assertEquals(attendu, Ollama.messageErreur(base, ConnectException("Failed to connect to /192.168.0.162:11434")))
        assertEquals(attendu, Ollama.messageErreur(base, UnknownHostException("ia162")))
        assertEquals(
            attendu,
            Ollama.messageErreur(base, SocketTimeoutException("failed to connect to /192.168.0.162 (port 11434) after 6000ms")),
        )
    }

    @Test
    fun `une lecture trop lente n'est pas presentee comme une absence du serveur`() {
        val message = Ollama.messageErreur("http://192.168.0.162:11434", SocketTimeoutException("timeout"))
        assertTrue(message, message.contains("trop de temps"))
        assertFalse(message.contains("Wi-Fi"))
    }

    @Test
    fun `une adresse invalide est signalee comme telle`() {
        val message = Ollama.messageErreur("http://adresse cassee", IllegalArgumentException("Invalid URL host"))
        assertTrue(message, message.startsWith("Adresse Ollama invalide"))
    }

    @Test
    fun `les autres erreurs gardent leur message`() {
        assertEquals("Ollama a répondu 500", Ollama.messageErreur("http://h:1", IOException("Ollama a répondu 500")))
    }

    @Test
    fun `seule une connexion reussie n'est pas une erreur`() {
        assertFalse(Ollama.estEtatErreur("Connecté · 3 modèle(s)"))
        assertFalse(Ollama.estEtatErreur("Serveur joignable, aucun modèle installé"))
        assertTrue(Ollama.estEtatErreur("Ollama injoignable sur ia162 : es-tu sur le Wi-Fi de la maison ?"))
    }
}
