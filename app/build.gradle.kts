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

    // Real release signing comes from the environment (CI secrets); locally it falls back to the debug key.
    val keystore = providers.environmentVariable("WARDEN_KEYSTORE").orNull
    signingConfigs {
        create("release") {
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("WARDEN_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("WARDEN_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("WARDEN_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
        }
    }

    buildFeatures.compose = true
}

dependencies {
    implementation(project(":core-engine"))
    implementation(project(":core-rules"))
    implementation(project(":core-design"))
    implementation(project(":feature-dashboard"))
    implementation(project(":feature-apps"))
    implementation(project(":feature-map"))
    implementation(project(":feature-logs"))
    implementation(project(":feature-settings"))
    implementation(libs.activity.compose)
    implementation(libs.haze)
    implementation(libs.haze.glass)
    debugImplementation(libs.compose.ui.tooling)
}
