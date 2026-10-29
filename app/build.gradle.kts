plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.warden"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.warden"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1-spike"
    }

    buildFeatures.compose = true
}

dependencies {
    implementation(project(":core-engine"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
