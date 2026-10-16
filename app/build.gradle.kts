import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: the keystore and its properties file live outside the repo.
// Lookup order: Gradle property `navshort.keystoreProperties`, env var `NAVSHORT_KEYSTORE_PROPERTIES`,
// default `~/.config/navigation-shortcuts/keystore.properties`.
// Expected keys: storeFile (absolute path), storePassword, keyAlias, keyPassword.
val keystorePropertiesPath: String =
    providers.gradleProperty("navshort.keystoreProperties").orNull
        ?: providers.environmentVariable("NAVSHORT_KEYSTORE_PROPERTIES").orNull
        ?: "${System.getProperty("user.home")}/.config/navigation-shortcuts/keystore.properties"
val keystorePropertiesFile = file(keystorePropertiesPath)
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}
val releaseSigningAvailable = keystorePropertiesFile.isFile

android {
    namespace = "io.github.dreadboy.navigationshortcuts"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.dreadboy.navigationshortcuts"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (releaseSigningAvailable) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
            // v1 is pointless with minSdk 31; v3 enables future key rotation.
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Fail clearly, and only when a release build is actually signed, if the keystore properties are missing.
// Debug builds, unit tests and lint work without them.
if (!releaseSigningAvailable) {
    val missingMessage =
        "Release signing is not configured: keystore properties file not found at " +
            "'${keystorePropertiesFile.absolutePath}'. Set the Gradle property " +
            "'navshort.keystoreProperties' or the env var 'NAVSHORT_KEYSTORE_PROPERTIES' to its path, " +
            "or create it at the default location (see docs/release-procedure.md)."
    tasks.matching { it.name == "validateSigningRelease" }.configureEach {
        doFirst { throw GradleException(missingMessage) }
    }
}

dependencies {
    implementation(project(":anycons"))
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
