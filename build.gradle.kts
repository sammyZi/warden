plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
}

// One detekt run over every module's sources from the root; ktlint rules come in via detekt-formatting.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt.yml"))
    source.setFrom(fileTree(rootDir) { include("*/src/**/*.kt") })
    autoCorrect = providers.gradleProperty("fix").isPresent // ./gradlew detekt -Pfix

}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

// detekt 1.23.8 embeds Kotlin 2.0.21; keep the project's newer Kotlin off its classpath.
configurations.matching { it.name.startsWith("detekt") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") useVersion("2.0.21")
    }
}
