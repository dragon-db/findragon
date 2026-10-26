plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.jdtech.jellyfin.data"
    compileSdk = Versions.COMPILE_SDK
    buildToolsVersion = Versions.BUILD_TOOLS

    defaultConfig {
        minSdk = Versions.MIN_SDK

        buildConfigField("int", "VERSION_CODE", FindragonVersions.APP_CODE.toString())
        buildConfigField("String", "VERSION_NAME", "\"${FindragonVersions.APP_NAME}\"")
        FindragonBuildConfig.buildConfigFields(project).forEach { (type, name, value) ->
            buildConfigField(type, name, value)
        }

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
