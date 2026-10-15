package com.zendolauncher.core

/**
 * One bundled wallpaper design. Three exist, deliberately: a quiet field, the
 * reminder line, and the principle with the wordmark. All grayscale, all drawn
 * on demand at the device's own size — no assets, no network.
 *
 * The design canvas is 9:20, and nothing sits above 34% height: the lock
 * clock owns the top of the screen. Type, when a design carries it, starts
 * below that zone.
 */
data class WallpaperTemplate(
    val id: String,
    val name: String,
    /** Base color ARGB. */
    val baseColor: Long,
    val description: String = "",
    /** The reminder line, set below the clear zone. */
    val caption: String? = null,
    /** Small letter-spaced wordmark beneath the caption. */
    val wordmark: String? = null,
    /** True when light text reads well on it (dark wallpaper). */
    val darkBackground: Boolean = true,
)

object WallpaperTemplates {

    /** Quiet Stone ships as the default selection. */
    const val DEFAULT_ID = "quiet_stone"

    val ALL: List<WallpaperTemplate> = listOf(
        WallpaperTemplate(
            id = DEFAULT_ID,
            name = "Quiet Stone",
            baseColor = 0xFF1B1C1E,
            description = "A quiet stone field. Nothing else.",
            darkBackground = true,
        ),
        WallpaperTemplate(
            id = "kembali",
            name = "Kembali",
            baseColor = 0xFF101112,
            description = "Kembali ke yang penting. — set quietly, below the clock.",
            caption = "Kembali ke yang penting.",
            darkBackground = true,
        ),
        WallpaperTemplate(
            id = "less_but_better",
            name = "Less, but better",
            baseColor = 0xFF101112,
            description = "The principle, with the wordmark beneath it.",
            caption = "Less, but better",
            wordmark = "ZENDO",
            darkBackground = true,
        ),
    )

    fun byId(id: String?): WallpaperTemplate? = ALL.firstOrNull { it.id == id }

    /** The default design. Always present, so callers never need a null path. */
    fun default(): WallpaperTemplate = ALL.first { it.id == DEFAULT_ID }
}
