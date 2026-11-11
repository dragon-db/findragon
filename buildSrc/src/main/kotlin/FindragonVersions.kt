/**
 * Findragon version scheme: `<upstream version>.<fork patch>`, e.g. upstream 1.1.0 + patch 1 =
 * 1.1.0.1.
 *
 * The upstream part is read from upstream's [Versions] (never edit that file, it changes every
 * upstream release). Bump [FORK_PATCH] for every APK shipped to users and reset it to 1 after
 * merging a new upstream release.
 */
object FindragonVersions {
    const val FORK_PATCH = 1

    val APP_CODE: Int
        get() {
            require(FORK_PATCH in 1..999) { "FORK_PATCH must be between 1 and 999." }
            return Versions.APP_CODE * 1000 + FORK_PATCH
        }

    val APP_NAME: String
        get() = "${Versions.APP_NAME}.$FORK_PATCH"
}
