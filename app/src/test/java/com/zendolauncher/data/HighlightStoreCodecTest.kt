package com.zendolauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.core.Highlight
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** org.json ships on Android, so encode/decode round-trips run under Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class HighlightStoreCodecTest {

    @Test
    fun `highlights survive a round trip`() {
        val items = listOf(
            Highlight(id = "h1", title = "Finish the important work.", note = "Prototype", isPrimary = true),
            Highlight(id = "h2", title = "Archived", archived = true),
        )
        val decoded = HighlightStore.decode(HighlightStore.encode(items))
        assertEquals(items.map { it.id }, decoded.map { it.id })
        assertEquals(items.map { it.title }, decoded.map { it.title })
    }

    @Test
    fun `malformed reads as empty instead of throwing`() {
        assertEquals(emptyList<Highlight>(), HighlightStore.decode(null))
        assertEquals(emptyList<Highlight>(), HighlightStore.decode("not json"))
        assertEquals(emptyList<Highlight>(), HighlightStore.decode("[]"))
    }
}
