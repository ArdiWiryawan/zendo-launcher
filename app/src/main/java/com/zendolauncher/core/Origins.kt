package com.zendolauncher.core

/**
 * Where a stored record came from. Plain text labels — never accounts or tokens.
 *
 * `local` is everything the user typed in the launcher itself. `zendo-pwa` survives as a
 * recognised value so a record written by an older build (which could import a Zendo
 * snapshot file) still round-trips and still displays honestly in History; nothing in
 * this build creates one, because the manual import was removed.
 */
object Origins {
    const val LOCAL = "local"
    const val ZENDO_PWA = "zendo-pwa"

    fun isImported(origin: String?): Boolean = origin == ZENDO_PWA

    /** Repairs untrusted text into a short label; unknown values fall back to local. */
    fun normalize(origin: String?): String =
        origin?.trim()?.takeIf { it.isNotEmpty() }?.take(64) ?: LOCAL
}
