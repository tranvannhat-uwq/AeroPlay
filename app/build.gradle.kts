import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val appVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val signingValues = Properties().apply {
    rootProject.file(".private/signing.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun signingValue(name: String, local: String): String? = System.getenv(name) ?: signingValues.getProperty(local)
val releaseStore = signingValue("AEROPLAY_KEYSTORE_PATH", "storeFile")
val updateRepository = providers.gradleProperty("githubRepository").getOrElse("tranvannhat-uwq/AeroPlay")
val youtubeWebClientId = providers.gradleProperty("youtubeOAuthWebClientId").getOrElse("")
val youtubeSyncRequested = providers.gradleProperty("youtubeAccountSyncEnabled").getOrElse("false").toBoolean()
val youtubeApiOnlyModeRequested = providers.gradleProperty("youtubeApiOnlyMode").getOrElse("false").toBoolean()
require(youtubeWebClientId.isBlank() || youtubeWebClientId.endsWith(".apps.googleusercontent.com")) {
    "youtubeOAuthWebClientId must be a Google OAuth Web client ID."
}
require(!youtubeSyncRequested || youtubeApiOnlyModeRequested) {
    "YouTube account sync requires youtubeApiOnlyMode=true because the current NewPipe playback path is not an approved YouTube API player."
}
val youtubeAccountSyncEnabled = youtubeSyncRequested && youtubeApiOnlyModeRequested && youtubeWebClientId.isNotBlank()
require(updateRepository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Invalid githubRepository" }

android {
    namespace = "com.aeroplay.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aeroplay.app"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("appVersionCode").orNull?.toInt() ?: appVersion.getProperty("VERSION_CODE").toInt()
        versionName = providers.gradleProperty("appVersionName").orNull ?: appVersion.getProperty("VERSION_NAME")
        buildConfigField("String", "UPDATE_REPOSITORY", "\"$updateRepository\"")
        buildConfigField("boolean", "YOUTUBE_ACCOUNT_SYNC_ENABLED", youtubeAccountSyncEnabled.toString())
        buildConfigField("boolean", "YOUTUBE_API_ONLY_MODE", youtubeApiOnlyModeRequested.toString())
        buildConfigField("String", "YOUTUBE_OAUTH_WEB_CLIENT_ID", "\"$youtubeWebClientId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("githubRelease") {
            releaseStore?.let { storeFile = rootProject.file(it) }
            storePassword = signingValue("AEROPLAY_KEYSTORE_PASSWORD", "storePassword")
            keyAlias = signingValue("AEROPLAY_KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("AEROPLAY_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("githubRelease")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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

    testOptions.unitTests.all {
        it.systemProperty("aeroplay.liveTests", providers.gradleProperty("aeroplay.liveTests").getOrElse("false"))
        it.testLogging.showStandardStreams = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

tasks.register("verifyReleaseSigning") {
    doLast {
        check(releaseStore != null && rootProject.file(releaseStore).isFile) {
            "Configure AEROPLAY_KEYSTORE_PATH or .private/signing.properties before building a signed release. See GITHUB_RELEASES.md."
        }
    }
}
tasks.configureEach { if (name == "preReleaseBuild") dependsOn("verifyReleaseSigning") }

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource.okhttp)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)
    implementation(libs.google.play.services.auth)

    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Networking & Image Loading
    implementation(libs.okhttp)
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.kotlinx.coroutines.play.services)

    // Experimental Extractor Source (NewPipeExtractor - GPLv3)
    implementation(libs.newpipe.extractor)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
