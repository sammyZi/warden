plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.warden.rules"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) { minorApiLevel = libs.versions.compileSdkMinor.get().toInt() }
    }
    defaultConfig.minSdk = libs.versions.minSdk.get().toInt()
}

dependencies {
    api(libs.coroutines.core)
    testImplementation(libs.junit)
}
