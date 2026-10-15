package com.zendolauncher.model

/** One app's foreground time. Numbers only — never used for enforcement. */
data class AppUsage(
    val packageName: String,
    val minutes: Int,
)
