package com.zendolauncher.core

enum class WallpaperCategory { SOLID, SUBTLE, PATTERN }

enum class WallpaperPattern {
    NONE, GRADIENT, GRAIN, LINES, TWO_TONE, TONAL,
}

/**
 * Bundled minimalist templates. All grayscale-first, no network, lightweight:
 * preview is Compose, apply renders a Bitmap on demand (no heavy assets).
 */
data class WallpaperTemplate(
    val id: String,
    val name: String,
    val category: WallpaperCategory,
    val pattern: WallpaperPattern = WallpaperPattern.NONE,
    /** Base color ARGB. */
    val baseColor: Long,
    /** Secondary color for gradient / two-tone / lines. */
    val secondaryColor: Long? = null,
    val description: String = "",
    /** True when light text reads well on it (dark wallpaper). */
    val darkBackground: Boolean = true,
)

object WallpaperTemplates {

    val ALL: List<WallpaperTemplate> = listOf(
        WallpaperTemplate(
            id = "obsidian", name = "Obsidian", category = WallpaperCategory.SOLID,
            baseColor = 0xFF000000, description = "Pure black. Least visual noise.", darkBackground = true,
        ),
        WallpaperTemplate(
            id = "paper", name = "Paper", category = WallpaperCategory.SOLID,
            baseColor = 0xFFF7F7F5, description = "Near-white paper.", darkBackground = false,
        ),
        WallpaperTemplate(
            id = "stone", name = "Stone", category = WallpaperCategory.SOLID,
            baseColor = 0xFFA8A8A6, description = "Neutral medium gray.", darkBackground = false,
        ),
        WallpaperTemplate(
            id = "graphite", name = "Graphite", category = WallpaperCategory.SOLID,
            baseColor = 0xFF2B2D30, description = "Dark gray, softer than black.", darkBackground = true,
        ),
        WallpaperTemplate(
            id = "mist", name = "Mist", category = WallpaperCategory.SOLID,
            baseColor = 0xFFE4E6E8, description = "Light cool gray.", darkBackground = false,
        ),
        WallpaperTemplate(
            id = "soft_gradient", name = "Soft gradient", category = WallpaperCategory.SUBTLE,
            pattern = WallpaperPattern.GRADIENT,
            baseColor = 0xFF0B0B0C, secondaryColor = 0xFF2A2A2C,
            description = "Quiet vertical monochrome gradient.", darkBackground = true,
        ),
        WallpaperTemplate(
            id = "soft_grain", name = "Soft grain", category = WallpaperCategory.SUBTLE,
            pattern = WallpaperPattern.GRAIN,
            baseColor = 0xFF1B1B1D, secondaryColor = 0xFF2E2E30,
            description = "Very subtle fine grain on dark.", darkBackground = true,
        ),
        WallpaperTemplate(
            id = "linear", name = "Linear", category = WallpaperCategory.SUBTLE,
            pattern = WallpaperPattern.LINES,
            baseColor = 0xFFF7F7F5, secondaryColor = 0xFFE2E2DE,
            description = "Minimal horizontal hairline on paper.", darkBackground = false,
        ),
        WallpaperTemplate(
            id = "duotone", name = "Two-tone", category = WallpaperCategory.SUBTLE,
            pattern = WallpaperPattern.TWO_TONE,
            baseColor = 0xFF0B0B0C, secondaryColor = 0xFFF7F7F5,
            description = "Quiet dark-light split, mostly dark.", darkBackground = true,
        ),
        WallpaperTemplate(
            id = "tonal", name = "Tonal drift", category = WallpaperCategory.SUBTLE,
            pattern = WallpaperPattern.TONAL,
            baseColor = 0xFF3A3A3C, secondaryColor = 0xFF0B0B0C,
            description = "Soft grayscale tonal transition.", darkBackground = true,
        ),
        WallpaperTemplate(
            id = "charcoal", name = "Charcoal", category = WallpaperCategory.SOLID,
            baseColor = 0xFF17181A, description = "Near-black with a breath of gray.", darkBackground = true,
        ),
        WallpaperTemplate(
            id = "horizon", name = "Horizon", category = WallpaperCategory.SUBTLE,
            pattern = WallpaperPattern.LINES,
            baseColor = 0xFF0B0B0C, secondaryColor = 0xFF2A2A2C,
            description = "One quiet hairline on dark.", darkBackground = true,
        ),
    )

    fun byId(id: String?): WallpaperTemplate? = ALL.firstOrNull { it.id == id }

    fun byCategory(category: WallpaperCategory?): List<WallpaperTemplate> =
        if (category == null) ALL else ALL.filter { it.category == category }
}
