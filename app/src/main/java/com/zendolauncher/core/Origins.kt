package com.zendolauncher.core

/**
 * Where a stored record came from. Plain text labels — never accounts or tokens.
 * "local" is everything the user typed in the launcher itself; "zendo-pwa" is a
 * record that arrived through a manual Zendo snapshot file (see ZendoBridge).
 */
object Origins {
    const val LOCAL = "local"
    const val ZENDO_PWA = "zendo-pwa"

    fun isImported(origin: String?): Boolean = origin == ZENDO_PWA

    /** Repairs untrusted text into a short label; unknown values fall back to local. */
    fun normalize(origin: String?): String =
        origin?.trim()?.takeIf { it.isNotEmpty() }?.take(64) ?: LOCAL
}
