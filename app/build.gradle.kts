import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ------------------------------------------------------------
//  Where the cloud lives.
//
//  supabase.properties holds three values, all of them public by design:
//  the project URL, the anon key, and the Google OAuth *web* client id.
//  None is a secret — the anon key only ever acts as the signed-in user
//  because every table is behind row level security, and a client id is
//  meant to be read out of an app. They are read here rather than typed
//  into source so the same checkout can point at a different project by
//  editing one file. See SETUP.md.
//
//  Missing or still holding the placeholders, the app starts on a short
//  setup screen that says exactly what is missing — rather than a sign-in
//  button that could never work.
// ------------------------------------------------------------
val supabaseProps = Properties().apply {
    val f = rootProject.file("supabase.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String): String = (supabaseProps.getProperty(key) ?: "")
    .trim()
    .takeUnless { it.isEmpty() || it.startsWith("PASTE_") || it.startsWith("YOUR_") }
    ?: ""

// The release signing key, described by a gitignored keystore.properties at
// the project root (see keystore.properties.example). Kept out of this file
// and out of git so the key and its passwords never reach version control —
// but read from disk rather than the environment so a plain
// `./gradlew assembleRelease` just works on the machine that holds them.
//
// Absent, the release build falls back to the debug key: still installable,
// still testable, just not something to publish. Google sign-in is bound to
// whichever certificate actually signs the APK, so the two cases need
// different SHA-1 fingerprints registered with Google — see SETUP.md.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }
val releaseKeystore = keystoreProperties?.getProperty("storeFile")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.hanifedma.tally"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.hanifedma.tally"
        // Android 6.0. Reach, deliberately: this is roughly 99% of the
        // Android phones actually in use, and it is as low as Tally can go —
        // Credential Manager's Google sign-in needs 23, and below that there
        // is no sign-in to offer. Everything newer than API 26 that the app
        // wants (java.time, most of all) comes from desugaring instead.
        minSdk = 23
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${cfg("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${cfg("supabase.anonKey")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${cfg("google.webClientId")}\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystoreProperties?.getProperty("storePassword")
                keyAlias = keystoreProperties?.getProperty("keyAlias")
                keyPassword = keystoreProperties?.getProperty("keyPassword")
                // Both schemes: v1 keeps Android 6 and older able to install
                // it, v2/v3 are what everything since verifies with.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Deliberately unminified. Ktor and kotlinx.serialization — the
            // two libraries the whole sync layer rests on — resolve types
            // reflectively at the edges, and a wrong keep rule fails at
            // runtime on a user's phone rather than at build time here. A few
            // extra megabytes is the right trade for a sync layer that cannot
            // silently break in release but work in debug.
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // java.time on API 24. Every date in this app is a LocalDate, so this
        // is load-bearing rather than a nicety.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Cloud: the same Postgres tables the web app reads and writes, and the
    // same realtime stream that keeps the two in step.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)

    // The account picture in the top bar, and nothing else.
    implementation(libs.coil.compose)

    // Google sign-in through Credential Manager.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
