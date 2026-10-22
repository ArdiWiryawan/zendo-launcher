package com.zendolauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.core.DayRecord
import com.zendolauncher.core.Highlight
import com.zendolauncher.core.Origins
import com.zendolauncher.core.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * The launcher's own backup file. The Zendo snapshot import is gone, so this class now
 * covers one format: what the launcher writes, what it accepts back, and what it refuses.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BridgeCodecTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 15)

    @Test
    fun `a backup round trip restores action, lists, history and settings`() {
        val day = DayRecord.capture(
            today.minusDays(1),
            actionTitle = "Yesterday's action",
            actionDone = true,
            highlightText = "A good walk",
            tasks = listOf(TaskItem(id = "t1", title = "Plain task")),
        )
        val contents = BackupContents(
            actionTitle = "Ship the deck",
            actionDone = false,
            actionOrigin = Origins.ZENDO_PWA,
            actionSourceId = "a1",
            highlights = listOf(Highlight(id = "h1", title = "Keep it small")),
            tasks = listOf(
                TaskItem(id = "t2", title = "Imported", origin = Origins.ZENDO_PWA, sourceId = "p1"),
            ),
            days = listOf(day),
            prefs = BackupPrefs(pauseEnabled = true, reduceMotion = true, theme = ThemeMode.LIGHT),
        )

        val raw = BridgeJson.encodeBackup(contents, "0.7.0", 1_790_000_000_000L)
        val parsed = (BridgeJson.parseBackup(raw) as BackupParse.Ok).contents

        assertEquals("Ship the deck", parsed.actionTitle)
        assertEquals(Origins.ZENDO_PWA, parsed.actionOrigin)
        assertEquals("a1", parsed.actionSourceId)
        assertEquals("Keep it small", parsed.highlights.single().title)
        assertEquals(Origins.ZENDO_PWA, parsed.tasks.single().origin)
        assertEquals("p1", parsed.tasks.single().sourceId)
        val restoredDay = parsed.days.single()
        assertEquals(today.minusDays(1), restoredDay.date)
        assertTrue(restoredDay.actionDone)
        assertTrue(parsed.prefs.pauseEnabled)
        assertTrue(parsed.prefs.reduceMotion)
        assertEquals(ThemeMode.LIGHT, parsed.prefs.theme)
    }

    /**
     * A file written by an older build still carries the controls this build removed
     * (layout, icon style, pin limit, screen time). Those keys must be ignored rather than
     * rejected — and, just as importantly, must not reappear in the parsed prefs.
     */
    @Test
    fun `an older backup with removed settings still restores`() {
        val raw = BridgeJson.encodeBackup(BackupContents(), "0.5.0", 0L)
            .replace(
                "\"settings\":{",
                "\"settings\":{\"layout\":\"ICON_TEXT\",\"monochromeIcons\":false," +
                    "\"pinLimit\":99,\"screenTimeEnabled\":true,\"clock24h\":false,",
            )
        val parsed = (BridgeJson.parseBackup(raw) as BackupParse.Ok).contents
        assertTrue(parsed.prefs.pauseMessage.isNotBlank())
    }

    @Test
    fun `backup parsing rejects other files and newer versions`() {
        val wrong = BridgeJson.parseBackup("""{"format":"something-else","schemaVersion":1}""")
            as BackupParse.Rejected
        assertTrue(wrong.message.contains("not a Zendo Launcher backup"))

        val notJson = BridgeJson.parseBackup("not json at all") as BackupParse.Rejected
        assertTrue(notJson.message.contains("not a valid JSON"))

        val empty = BridgeJson.parseBackup("") as BackupParse.Rejected
        assertTrue(empty.message.contains("empty"))

        val noVersion = BridgeJson.parseBackup("""{"format":"zendo-launcher-backup"}""")
            as BackupParse.Rejected
        assertTrue(noVersion.message.contains("schemaVersion"))

        val newer = BridgeJson.encodeBackup(BackupContents(), "0.7.0", 0L)
            .replace("\"schemaVersion\":1", "\"schemaVersion\":2")
        val rejected = BridgeJson.parseBackup(newer) as BackupParse.Rejected
        assertTrue(rejected.message.contains("newer"))
    }

    @Test
    fun `a damaged record is dropped instead of poisoning the whole file`() {
        val raw = BridgeJson.encodeBackup(
            BackupContents(highlights = listOf(Highlight(id = "h1", title = "Kept"))),
            "0.7.0",
            0L,
        ).replace("\"title\":\"Kept\"", "\"title\":\"\"")
        val parsed = BridgeJson.parseBackup(raw) as BackupParse.Ok
        assertTrue(parsed.contents.highlights.isEmpty())
    }

    @Test
    fun `a backup with no settings section parses to defaults`() {
        val raw = BridgeJson.encodeBackup(BackupContents(), "0.7.0", 0L)
            .replace(",\"settings\":{", ",\"settingsUnused\":{")
        val parsed = BridgeJson.parseBackup(raw) as BackupParse.Ok
        assertEquals(ThemeMode.DARK, parsed.contents.prefs.theme)
        assertFalse(parsed.contents.prefs.pauseEnabled)
    }
}
