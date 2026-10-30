import java.io.File
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

fun releaseKeystore(): File? {
    System.getenv("RSSFABRICE_KEYSTORE_B64")?.takeIf { it.isNotBlank() }?.let { b64 ->
        return try {
            val tmp = File(System.getenv("RUNNER_TEMP") ?: "/tmp", "rssfabrice-release.keystore")
            tmp.writeBytes(Base64.getDecoder().decode(b64))
            tmp
        } catch (e: Exception) {
            null
        }
    }
    val candidats = listOf(
        File("/root/.secrets/keystores-android/rssfabrice-release.keystore"),
        File(System.getProperty("user.home"), ".secrets/keystores-android/rssfabrice-release.keystore"),
    )
    return candidats.firstOrNull { it.exists() }
}

/** Une variable d'environnement vide (secret GitHub non renseigné) compte comme absente. */
fun secret(nom: String, defaut: String): String =
    System.getenv(nom)?.takeIf { it.isNotBlank() } ?: defaut

android {
    namespace = "fr.fh.rss"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.fh.rss"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.2.0"
        resourceConfigurations += listOf("fr")
    }

    val ks = releaseKeystore()
    if (ks != null) {
        signingConfigs {
            create("release") {
                storeFile = ks
                storePassword = secret("RSSFABRICE_KEYSTORE_PASSWORD", "rssfabrice2026")
                keyAlias = secret("RSSFABRICE_KEY_ALIAS", "rssfabrice")
                keyPassword = secret("RSSFABRICE_KEY_PASSWORD", "rssfabrice2026")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (ks != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Base locale + recherche plein texte
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Préférences (hors base, comme le prévoit le cahier des charges)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Collecte périodique optionnelle
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Réseau + analyse des flux et du HTML
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    // org.json est un stub vide dans les tests JVM : il faut la vraie implémentation
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
