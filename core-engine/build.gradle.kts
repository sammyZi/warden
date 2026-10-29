plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.warden.engine"
    compileSdk = 37
    defaultConfig.minSdk = 26
}

dependencies {
    api(libs.coroutines.core)
    // Published with <packaging>jar</packaging> but the file is an AAR.
    api(libs.firestack) { artifact { type = "aar" } }
}
