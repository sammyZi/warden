plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.warden.settings"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) { minorApiLevel = libs.versions.compileSdkMinor.get().toInt() }
    }
    defaultConfig.minSdk = libs.versions.minSdk.get().toInt()
    buildFeatures.compose = true
}

dependencies {
    implementation(project(":core-design"))
    implementation(project(":core-rules"))
    implementation(project(":core-data"))
    implementation(libs.activity.compose)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}
