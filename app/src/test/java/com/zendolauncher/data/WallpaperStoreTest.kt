package com.zendolauncher.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The applied-id record: "match wallpaper" must follow the design the last *apply* sent
 * to the system, not the gallery highlight a tap moves. The two are separate keys on
 * disk, and the applied record survives a new store instance — a relaunch reads back the
 * design that is actually on the device, which is the one the wash may follow.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class WallpaperStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val prefs = context.getSharedPreferences("launcher_wallpaper", Context.MODE_PRIVATE)

    @Before
    fun clearPrefs() {
        prefs.edit { clear() }
    }

    @Test
    fun `a fresh store has nothing applied`() {
        assertNull(WallpaperStore(context).appliedId.value)
    }

    @Test
    fun `the applied record survives a new store and stays separate from the highlight`() {
        WallpaperStore(context).apply {
            select("less_but_better")
            recordApplied("kembali")
        }

        val reopened = WallpaperStore(context)
        assertEquals("kembali", reopened.appliedId.value)
        assertEquals("less_but_better", reopened.selectedId.value)
    }
}
