plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.warden"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.warden"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 2
        versionName = "0.1.0-m1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // ponytail: debug key so optimized builds can be side-loaded for perf testing; real signing in M8.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures.compose = true
}

dependencies {
    implementation(project(":core-engine"))
    implementation(project(":core-design"))
    implementation(project(":feature-dashboard"))
    implementation(project(":feature-apps"))
    implementation(project(":feature-map"))
    implementation(project(":feature-logs"))
    implementation(project(":feature-settings"))
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
}
