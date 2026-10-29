plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.warden.rules"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig.minSdk = libs.versions.minSdk.get().toInt()
}

dependencies {
    api(libs.coroutines.core)
    testImplementation(libs.junit)
}
