plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.warden.dashboard"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) { minorApiLevel = libs.versions.compileSdkMinor.get().toInt() }
    }
    defaultConfig.minSdk = libs.versions.minSdk.get().toInt()
    buildFeatures.compose = true
}

dependencies {
    implementation(project(":core-design"))
    implementation(project(":core-engine"))
    implementation(project(":core-data"))
    implementation(project(":core-rules"))
    implementation(libs.activity.compose)
}
