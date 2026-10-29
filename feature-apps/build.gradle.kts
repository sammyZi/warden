plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.warden.apps"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig.minSdk = libs.versions.minSdk.get().toInt()
    buildFeatures.compose = true
}

dependencies {
    implementation(project(":core-design"))
    implementation(project(":core-engine"))
    implementation(libs.activity.compose)
}
