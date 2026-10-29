plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.warden.engine"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig.minSdk = libs.versions.minSdk.get().toInt()
}

dependencies {
    api(libs.coroutines.core)
    // Published with <packaging>jar</packaging> but the file is an AAR.
    api(libs.firestack) { artifact { type = "aar" } }
}
