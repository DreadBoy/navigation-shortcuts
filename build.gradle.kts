plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin); this only pins the Kotlin Gradle plugin version.
    alias(libs.plugins.kotlin.android) apply false
}
