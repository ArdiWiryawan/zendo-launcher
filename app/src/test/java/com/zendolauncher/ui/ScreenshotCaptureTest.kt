package com.zendolauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.LauncherActivity
import com.zendolauncher.core.PageType
import com.zendolauncher.core.WallpaperTemplates
import com.zendolauncher.data.IconStyle
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.data.SettingsStore
import com.zendolauncher.data.ThemeMode
import com.zendolauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Renders the real launcher screens on the JVM and writes them to `build/screenshots`.
 *
 * Two things this deliberately does NOT do, both of which sank an earlier attempt:
 *
 * 1. It does not use `captureToImage()`. On Robolectric that goes through PixelCopy and
 *    `forceRedraw`, which never completes — it throws ComposeTimeoutException after 2s.
 *    The view is drawn into a software bitmap through the ordinary `View.draw` path
 *    instead, which is what actually produces glyphs here.
 * 2. It does not treat a file's existence or size as evidence. Every capture is decoded
 *    and inspected in-process: distinct colours, pixels differing from the modal
 *    background, and a SHA-256 that must be unique across the run. A flat image, or two
 *    captures that are the same bytes, fails the test.
 *
 * Robolectric discovers no installed apps, so the drawer and Essentials are empty. That
 * is the platform's answer here, not a rendering fault, and the images say so.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotCaptureTest {

    @get:Rule
    val rule = createAndroidComposeRule<LauncherActivity>()

    private val viewModel: LauncherViewModel
        get() = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]

    private val outDir = File("build/screenshots").apply { mkdirs() }

    /** Every capture's digest, so a duplicate is caught rather than shipped. */
    private val seen = LinkedHashMap<String, String>()

    /**
     * The gold TINTED resolves to for the tinted capture, formatted the way
     * LauncherActivity formats a resolved accent (and read from the design's own
     * accent, so a palette change cannot leave the capture lying about it).
     */
    private val goldAccent: String = run {
        val accent = requireNotNull(WallpaperTemplates.byId("less_but_better")).dominantAccent()
        String.format(Locale.US, "#%06X", accent.toArgb() and 0xFFFFFF)
    }

    private fun contentView(): View {
        val content = rule.activity.findViewById<ViewGroup>(android.R.id.content)
        return if (content.childCount > 0) content.getChildAt(0) else content
    }

    /**
     * Brings the launcher to a settled, known state. The activity launches into
     * onboarding on a fresh Robolectric run, so this walks out of it the way a user
     * would before anything else is captured.
     */
    private fun prepare(mode: ThemeMode) {
        rule.waitForIdle()
        viewModel.dismissOnboarding()
        viewModel.setTheme(mode)
        viewModel.setLayout(LayoutMode.TEXT)
        viewModel.setTodayEnabled(true)
        viewModel.setClock24h(true)
        viewModel.goHome()
        rule.waitForIdle()
        rule.waitUntil(5_000) { !viewModel.state.value.isLoading }
        rule.waitForIdle()
    }

    /**
     * Installs [count] launchable fakes and waits for the rescan to land, so the dock
     * resolves against the same enumeration a device would hand it.
     */
    private fun installCalmApps(count: Int): List<String> {
        val keys = (1..count).map { index ->
            FakeApps.install(rule.activity, "com.example.calm$index", "Calm $index")
        }
        viewModel.refreshApps()
        rule.waitUntil(5_000) { viewModel.state.value.visibleApps.size >= count }
        rule.waitForIdle()
        return keys
    }

    /**
     * Shows Home with the Today dock: the real activity and view model over a crafted
     * state, because the Today page's item list has no in-app editor yet. [style] and
     * [tint] are provided the way LauncherActivity resolves them — the stored swatch for
     * MONOCHROME, or the selected design's accent for TINTED — and the page paints flat
     * unless [backdrop] asks for the design's field.
     */
    private fun showCalmHome(
        mode: ThemeMode,
        keys: List<String>,
        backdrop: Boolean = false,
        style: IconStyle = IconStyle.MONOCHROME,
        tint: String = SettingsStore.DEFAULT_ICON_TINT,
    ) {
        val base = viewModel.state.value
        val today = base.settings.home.pages.first { it.type == PageType.TODAY }
        val home = base.settings.home.updatePageConfig(today.id, today.config.copy(itemKeys = keys))
        val state = base.copy(
            settings = base.settings.copy(
                home = home,
                theme = mode,
                iconStyle = style,
                homeBackdrop = backdrop,
            ),
            pagerTargetPage = null,
        )
        // Warm the icon cache first: AppIcon loads bitmaps off the main thread, and a
        // cache hit keeps each of those round-trips down to one raster.
        val px = (40 * rule.activity.resources.displayMetrics.density).toInt()
        runBlocking {
            state.visibleApps.filter { it.key in keys }.forEach { viewModel.bitmapFor(it, px) }
        }
        rule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                LauncherTheme(mode = mode) {
                    CompositionLocalProvider(
                        LocalIconStyle provides style,
                        LocalIconTint provides tint,
                    ) {
                        HomeScreen(viewModel, state)
                    }
                }
            }
        }
        // Bounded frame loop: the one suspension left is the produceState that hands
        // each warmed bitmap back to its AppIcon, and a capture that skipped it would
        // ship empty slots.
        repeat(30) {
            rule.waitForIdle()
            Thread.sleep(10)
        }
        rule.waitForIdle()
    }

    /**
     * Draws the window's content view into a bitmap and checks the result before it is
     * written. The checks are the point: a solid rectangle of background colour satisfies
     * "the file exists", "the file has a plausible size" and even "there are pixels that
     * differ from the background" if the sampled pixel happens to be a glyph.
     */
    private fun capture(name: String) {
        rule.waitForIdle()

        val view = contentView()
        val width = view.width
        val height = view.height
        assertTrue("$name: view is unmeasured (${width}x$height)", width > 0 && height > 0)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Black, not the palette's own background: a Compose view that failed to draw
        // would otherwise leave a rectangle indistinguishable from a drawn one.
        bitmap.eraseColor(AndroidColor.BLACK)
        view.draw(Canvas(bitmap))

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val histogram = HashMap<Int, Int>(pixels.size)
        for (p in pixels) histogram[p] = (histogram[p] ?: 0) + 1
        // The modal colour is the background; sampling a single pixel would land on a
        // glyph often enough to make the count meaningless.
        val background = histogram.maxByOrNull { it.value }!!.key
        val nonBackground = pixels.size - histogram[background]!!

        // Strong ink: pixels far enough from the background in every channel to be text
        // or a hairline rather than the anti-aliasing fringe around them. A capture of a
        // page whose only content is a rectangle of background scores zero here, which is
        // exactly the failure a "non-background pixel" count alone does not catch.
        val ink = pixels.count { distance(it, background) > 90 }

        val file = File(outDir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { String.format(Locale.ROOT, "%02x", it) }

        seen[name] = sha
        val line = "$name ${width}x${height} distinct=${histogram.size} " +
            "nonBackground=$nonBackground ink=$ink " +
            "bg=#${String.format(Locale.ROOT, "%08X", background)} sha256=$sha"
        println("[screenshot] $line")
        File(outDir, "stats.txt").appendText(line + "\n")

        assertTrue("$name: only ${histogram.size} distinct colour(s) — effectively flat", histogram.size >= 8)
        assertTrue(
            "$name: only $nonBackground of ${pixels.size} pixels differ from the background",
            nonBackground >= 1_000,
        )
        assertTrue("$name: only $ink pixels of strong ink — nothing was drawn", ink >= 300)
    }

    /** Sum of absolute per-channel differences. Crude, and enough to separate ink from fringe. */
    private fun distance(a: Int, b: Int): Int =
        Math.abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) +
            Math.abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) +
            Math.abs((a and 0xFF) - (b and 0xFF))

    private fun assertAllDistinct() {
        val byHash = seen.entries.groupBy({ it.value }, { it.key })
        val collisions = byHash.filterValues { it.size > 1 }
        assertTrue("identical captures: $collisions", collisions.isEmpty())
    }

    @Test
    fun captureAllScreens() {
        // Onboarding first, because it is the only screen a fresh install shows and the
        // only one that is not reachable from Home. It is opened explicitly rather than
        // assumed: SharedPreferences survive for the whole Robolectric JVM, so whether
        // this run starts on onboarding depends on what ran before it. Asking for the
        // screen makes the capture independent of test order.
        rule.waitForIdle()
        viewModel.showOnboarding()
        rule.waitForIdle()
        assertTrue(
            "expected showOnboarding() to reach the onboarding screen",
            viewModel.state.value.screen == Screen.ONBOARDING,
        )
        capture("after-onboarding.png")

        prepare(ThemeMode.LIGHT)
        capture("after-home-light.png")

        viewModel.setTheme(ThemeMode.DARK)
        rule.waitForIdle()
        capture("after-home-dark.png")

        viewModel.openApps()
        rule.waitForIdle()
        rule.waitUntil(5_000) { !viewModel.state.value.isLoading }
        rule.waitForIdle()
        capture("after-apps.png")

        viewModel.setQuery("ca")
        rule.waitForIdle()
        capture("after-apps-search.png")

        viewModel.openSettings()
        rule.waitForIdle()
        capture("after-settings.png")

        // The Calm Canvas captures. The tinted one carries the dock, because icons are
        // the only place Home shows the icon tint: TINTED always follows the selected
        // design's accent, and less_but_better's is gold. The backdrop and the design key
        // come from the real store; the dock's items can only come in through a crafted
        // state, the same reason the dock capture below uses one.
        viewModel.goHome()
        viewModel.setTheme(ThemeMode.DARK)
        viewModel.setIconStyle(IconStyle.TINTED)
        viewModel.setHomeBackdrop(true)
        viewModel.selectWallpaper("less_but_better")
        rule.waitForIdle()
        val calmKeys = installCalmApps(4)
        showCalmHome(ThemeMode.DARK, calmKeys, backdrop = true, style = IconStyle.TINTED, tint = goldAccent)
        capture("calm-home-tint-backdrop-dark.png")

        // Then the dock on its own terms: one calm cream row on the flat page.
        viewModel.setIconStyle(IconStyle.MONOCHROME)
        viewModel.setHomeBackdrop(false)
        rule.waitForIdle()
        showCalmHome(ThemeMode.DARK, calmKeys)
        capture("calm-home-dock-dark.png")

        showCalmHome(ThemeMode.LIGHT, calmKeys)
        capture("calm-home-light.png")

        assertAllDistinct()
    }
}
