import java.io.StringReader
import java.net.URI
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Findragon private infra config, read from the git-ignored `findragon.properties` in the repo root
 * (template: `findragon.properties.example`).
 *
 * `FINDRAGON_INFRA` is the master switch. It is read from that file and can be overridden on the
 * command line with `-PFINDRAGON_INFRA=true|false`.
 * - true: every key in [URL_KEYS] is required and must be an https URL, otherwise the build fails.
 * - false or missing: every URL is empty, so all DragonDB integrations are switched off.
 *
 * The values end up as `BuildConfig` fields of the `data` module and are read in app code through
 * `dev.jdtech.jellyfin.findragon.FindragonConfig` only.
 */
object FindragonBuildConfig {
    const val FILE_NAME = "findragon.properties"
    const val FLAG = "FINDRAGON_INFRA"
    val URL_KEYS =
        listOf("DEFAULT_SERVER_URL", "JELLYSEERR_BASE_URL", "JFA_GO_BASE_URL", "DRAGON_HUB_URL")

    data class Field(val type: String, val name: String, val value: String)

    fun buildConfigFields(project: Project): List<Field> {
        val file = project.rootProject.layout.projectDirectory.file(FILE_NAME)
        val text = project.providers.fileContents(file).asText.orNull
        val properties = Properties().apply { text?.let { load(StringReader(it)) } }

        val enabled =
            parseFlag(project.providers.gradleProperty(FLAG).orNull ?: properties.getProperty(FLAG))
        if (enabled && text == null) {
            throw GradleException(
                "$FLAG=true but $FILE_NAME was not found in the repo root. " +
                    "Copy $FILE_NAME.example to $FILE_NAME and fill in the values."
            )
        }

        val urls = URL_KEYS.associateWith { key -> if (enabled) properties.cleanValue(key) else "" }
        if (enabled) {
            val errors = urls.mapNotNull { (key, url) -> validateUrl(key, url) }
            if (errors.isNotEmpty()) {
                throw GradleException(
                    "Invalid $FILE_NAME for a $FLAG=true build:\n" +
                        errors.joinToString("\n") { "  - $it" }
                )
            }
        }

        project.logger.lifecycle("Findragon infra build: ${if (enabled) "ON" else "OFF"}")

        return listOf(Field("boolean", FLAG, enabled.toString())) +
            urls.map { (key, url) -> Field("String", key, quote(url)) }
    }

    private fun parseFlag(raw: String?): Boolean {
        val value = raw?.trim()?.unquote()?.trim().orEmpty()
        if (value.isEmpty()) return false
        return value.toBooleanStrictOrNull()
            ?: throw GradleException("$FLAG must be true or false, got '$value'.")
    }

    private fun Properties.cleanValue(key: String): String =
        getProperty(key).orEmpty().trim().unquote().trim().trimEnd('/')

    private fun validateUrl(key: String, url: String): String? {
        if (url.isEmpty()) return "$key is missing"
        val uri = runCatching { URI(url) }.getOrNull() ?: return "$key is not a valid URL"
        return when {
            uri.scheme != "https" -> "$key must start with https://"
            uri.host.isNullOrBlank() -> "$key has no host"
            uri.userInfo != null -> "$key must not contain credentials"
            uri.query != null || uri.fragment != null -> "$key must not contain ? or #"
            else -> null
        }
    }

    private fun String.unquote(): String = removeSurrounding("\"").removeSurrounding("'")

    private fun quote(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
