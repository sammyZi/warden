plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.warden.data"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) { minorApiLevel = libs.versions.compileSdkMinor.get().toInt() }
    }
    defaultConfig.minSdk = libs.versions.minSdk.get().toInt()
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    testImplementation(libs.junit)
    api(project(":core-engine"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
