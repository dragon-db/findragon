import org.gradle.api.JavaVersion

object Versions {
    const val UPSTREAM_APP_CODE = 32
    const val FORK_PATCH_CODE = 5

    const val APP_CODE = UPSTREAM_APP_CODE * 1000 + FORK_PATCH_CODE
    const val APP_NAME = "1.0.2.5"

    const val COMPILE_SDK = 36
    const val TARGET_SDK = 36
    const val MIN_SDK = 28
    const val BUILD_TOOLS = "36.1.0"

    val JAVA = JavaVersion.VERSION_21
}
