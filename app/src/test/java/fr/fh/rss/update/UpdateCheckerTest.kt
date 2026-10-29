package fr.fh.rss.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `comparaison de versions segment par segment`() {
        assertTrue(UpdateChecker.compareVersions("0.10.0", "0.9.0") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "1.0.0") == 0)
        assertTrue(UpdateChecker.compareVersions("1.0.1", "1.0.0") > 0)
        assertTrue(UpdateChecker.compareVersions("1.2", "1.10") < 0)
        assertTrue(UpdateChecker.compareVersions("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun `manifeste json lu avant l'api`() {
        val corps = """{"version":"1.1.0","apk":"https://exemple.fr/app-release.apk","notes":"Corrections"}"""
        val resultat = UpdateChecker.parseManifest(corps, "1.0.0")
        assertTrue(resultat is UpdateChecker.CheckResult.Available)
        val dispo = resultat as UpdateChecker.CheckResult.Available
        assertEquals("1.1.0", dispo.info.version)
        assertEquals("https://exemple.fr/app-release.apk", dispo.info.apkUrl)
        assertEquals("Corrections", dispo.info.notes)

        assertTrue(UpdateChecker.parseManifest(corps, "1.1.0") is UpdateChecker.CheckResult.UpToDate)
        assertTrue(UpdateChecker.parseManifest(corps, "2.0.0") is UpdateChecker.CheckResult.UpToDate)
    }

    @Test
    fun `manifeste illisible renvoie null pour passer a l'api`() {
        assertEquals(null, UpdateChecker.parseManifest("<html>404</html>", "1.0.0"))
        assertEquals(null, UpdateChecker.parseManifest("""{"notes":"sans version"}""", "1.0.0"))
    }

    @Test
    fun `api github ignore les brouillons et choisit le bon apk`() {
        val releases = JSONArray(
            """
            [
              {"tag_name":"latest","name":"latest","draft":true,
               "assets":[{"name":"rss-fabrice-v1.3.0.apk","browser_download_url":"https://exemple.fr/draft.apk"}]},
              {"tag_name":"latest","name":"latest","draft":false,
               "body":"Nouveautés",
               "assets":[{"name":"rss-fabrice-v1.2.0.apk","browser_download_url":"https://exemple.fr/v120.apk"},
                         {"name":"version.json","browser_download_url":"https://exemple.fr/version.json"}]},
              {"tag_name":"v1.0.0","name":"v1.0.0","draft":false,
               "assets":[{"name":"app-release.apk","browser_download_url":"https://exemple.fr/v100.apk"}]}
            ]
            """.trimIndent(),
        )

        val resultat = UpdateChecker.parseApi(releases.toString(), "1.0.0")
        assertTrue(resultat is UpdateChecker.CheckResult.Available)
        val dispo = resultat as UpdateChecker.CheckResult.Available
        // La release roulante « latest » tire sa version du nom de l'APK.
        assertEquals("1.2.0", dispo.info.version)
        assertEquals("https://exemple.fr/v120.apk", dispo.info.apkUrl)
        assertEquals("Nouveautés", dispo.info.notes)
    }

    @Test
    fun `api github sans version plus recente`() {
        val releases = JSONArray(
            """[{"tag_name":"latest","name":"latest","draft":false,
                 "assets":[{"name":"rss-fabrice-v1.0.0.apk","browser_download_url":"https://exemple.fr/v100.apk"}]}]""",
        )
        assertTrue(UpdateChecker.parseApi(releases.toString(), "1.0.0") is UpdateChecker.CheckResult.UpToDate)
    }

    @Test
    fun `extraction de version depuis un titre`() {
        assertEquals("1.2.3", UpdateChecker.extractVersion("RSS v1.2.3"))
        assertEquals(null, UpdateChecker.extractVersion("latest"))
    }

    @Test
    fun `selection d'asset apk`() {
        val assets = JSONArray(
            """[{"name":"version.json","browser_download_url":"https://exemple.fr/version.json"},
                {"name":"rss-fabrice-v1.2.0.apk","browser_download_url":"https://exemple.fr/v120.apk"}]""",
        )
        assertEquals("https://exemple.fr/v120.apk", UpdateChecker.pickApk(assets, "1.2.0"))
        assertEquals(null, UpdateChecker.pickApk(JSONArray("[]"), "1.2.0"))
    }

    @Test
    fun `cadence des verifications`() {
        val maintenant = 10_000_000L
        assertTrue(UpdateSchedule.isDue(manual = true, lastCheckAt = maintenant, now = maintenant))
        assertTrue(UpdateSchedule.isDue(manual = false, lastCheckAt = UpdateSchedule.NEVER, now = maintenant))
        assertTrue(
            UpdateSchedule.isDue(
                manual = false,
                lastCheckAt = maintenant - UpdateSchedule.MIN_INTERVAL_MS - 1,
                now = maintenant,
            ),
        )
        assertTrue(
            !UpdateSchedule.isDue(
                manual = false,
                lastCheckAt = maintenant - 60_000,
                now = maintenant,
            ),
        )
    }

    @Test
    fun `declaration du depot public coherente`() {
        assertTrue(UpdateChecker.REPO.contains("/"))
        assertTrue(UpdateChecker.MANIFEST_URL.endsWith("/releases/download/latest/version.json"))
        assertTrue(UpdateChecker.APK_URL.endsWith("/releases/download/latest/app-release.apk"))
        assertNotNull(UpdateChecker.API_URL)
    }
}
