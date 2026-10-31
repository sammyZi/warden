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
        versionCode = 3
        versionName = "0.2.0"
    }

    // One APK per CPU type keeps downloads small (the engine's native library is ~20 MB per type),
    // plus a universal APK for anyone unsure which to pick.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = true
        }
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
    implementation(project(":core-data"))
    implementation(project(":core-design"))
    implementation(project(":feature-dashboard"))
    implementation(project(":feature-apps"))
    implementation(project(":feature-map"))
    implementation(project(":feature-logs"))
    implementation(project(":feature-settings"))
    implementation(libs.activity.compose)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    debugImplementation(libs.compose.ui.tooling)
}
