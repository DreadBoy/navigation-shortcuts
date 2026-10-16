plugins {
    alias(libs.plugins.android.test)
}

// End-to-end suite: drives the real Niagara launcher, the real widget and real Google Maps with UI Automator.
// A separate com.android.test module (not :app androidTest) because the suite must `am force-stop` the app
// under test (spec scenario 5): instrumentation inside :app runs in the app's own process and would be
// killed with it. Self-instrumenting = the test APK runs in its own process and only talks to the app
// through the UI. Run it with e2e/run-e2e.sh (or ./gradlew :e2e:connectedDebugAndroidTest on a prepared emulator).
android {
    namespace = "io.github.dreadboy.navigationshortcuts.e2e"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
