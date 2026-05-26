import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use(::load)
        }
    }

fun privateUrlProperty(name: String): String {
    val value =
        (providers.gradleProperty(name).orNull ?: localProperties.getProperty(name).orEmpty())
            .trim()
            .trimEnd('/')
    if (value.isEmpty()) {
        return ""
    }

    val uri =
        runCatching { URI(value) }
            .getOrElse { throw GradleException("$name must be a valid HTTP or HTTPS URL.") }
    if (
        uri.scheme !in setOf("http", "https") ||
            uri.host.isNullOrBlank() ||
            uri.userInfo != null
    ) {
        throw GradleException("$name must be a valid HTTP or HTTPS URL without credentials.")
    }
    return value
}

fun buildConfigString(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

val defaultServerUrl = privateUrlProperty("DEFAULT_SERVER_URL")

android {
    namespace = "dev.jdtech.jellyfin.setup"
    compileSdk = Versions.COMPILE_SDK
    buildToolsVersion = Versions.BUILD_TOOLS

    defaultConfig {
        minSdk = Versions.MIN_SDK
        buildConfigField("String", "DEFAULT_SERVER_URL", buildConfigString(defaultServerUrl))
    }

    buildTypes {
        named("release") { isMinifyEnabled = false }
        register("staging") { initWith(getByName("release")) }
    }

    compileOptions {
        sourceCompatibility = Versions.JAVA
        targetCompatibility = Versions.JAVA
    }

    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(projects.core)
    implementation(projects.data)
    implementation(projects.settings)
    implementation(libs.timber)

    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.jellyfin.core)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
