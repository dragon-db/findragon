import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
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

val jellyseerrBaseUrl = privateUrlProperty("JELLYSEERR_BASE_URL")

android {
    namespace = "dev.jdtech.jellyfin.data"
    compileSdk = Versions.COMPILE_SDK
    buildToolsVersion = Versions.BUILD_TOOLS

    defaultConfig {
        minSdk = Versions.MIN_SDK

        buildConfigField("int", "VERSION_CODE", Versions.APP_CODE.toString())
        buildConfigField("String", "VERSION_NAME", "\"${Versions.APP_NAME}\"")
        buildConfigField("String", "JELLYSEERR_BASE_URL", buildConfigString(jellyseerrBaseUrl))

        consumerProguardFile("proguard-rules.pro")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }
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
    implementation(projects.settings)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.security.crypto)
    ksp(libs.androidx.room.compiler)
    implementation(libs.jellyfin.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.timber)

    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
