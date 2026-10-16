plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.dreadboy.navigationshortcuts.anycons"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.maxHeapSize = "2g"
            // Niagara-backed tests decode vendor/niagara.apk with apktool into the build dir and are skipped
            // (JUnit Assume) when the APK or apktool is missing, so CI without the APK stays green.
            it.systemProperty("anycons.buildDir", layout.buildDirectory.get().asFile.absolutePath)
            it.systemProperty("anycons.rootDir", rootDir.absolutePath)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
