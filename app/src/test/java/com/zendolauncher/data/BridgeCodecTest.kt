package com.zendolauncher.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.core.BridgeParse
import com.zendolauncher.core.DayRecord
import com.zendolauncher.core.Highlight
import com.zendolauncher.core.Origins
import com.zendolauncher.core.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BridgeCodecTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 15)

    private val validSnapshot = """
        {
          "format": "zendo-launcher-bridge",
          "schemaVersion": 1,
          "exportedAt": "2026-10-15T08:00:00+07:00",
          "source": "zendo-pwa",
          "localDate": "2026-10-15",
          "timeZone": "Asia/Jakarta",
          "todayAction": {"id": "a1", "title": "Ship the deck", "note": "before noon"},
          "highlight": {"id": "h1", "text": "Walk after lunch"},
          "tasks": [
            {"id": "p1", "title": "Draft outline", "isCompleted": false, "dueDate": "2026-10-15", "sortOrder": 0, "updatedAt": "2026-10-15T07:00:00+07:00"},
            {"id": "p2", "title": "Send invoice", "isCompleted": true, "sortOrder": 1}
          ]
        }
    """.trimIndent()

    private fun ok(raw: String, today: LocalDate = this.today) =
        (BridgeJson.parseSnapshot(raw, today) as BridgeParse.Ok).snapshot

    private fun reject(raw: String, today: LocalDate = this.today) =
        BridgeJson.parseSnapshot(raw, today) as BridgeParse.Rejected

    @Test
    fun `a valid snapshot parses with every field in place`() {
        val snapshot = ok(validSnapshot)
        assertEquals("zendo-pwa", snapshot.source)
        assertEquals(today, snapshot.localDate)
        assertEquals("Asia/Jakarta", snapshot.timeZone)
        assertEquals("Ship the deck", snapshot.action!!.title)
        assertEquals("Walk after lunch", snapshot.highlight!!.text)
        assertEquals(2, snapshot.tasks.size)
        assertEquals(today, snapshot.tasks.first().dueDate)
        assertTrue(snapshot.tasks[1].done)
        assertFalse(snapshot.isEmpty)
    }

    @Test
    fun `optional sections may be absent`() {
        val minimal = """
            {"format":"zendo-launcher-bridge","schemaVersion":1,
             "exportedAt":"2026-10-15T08:00:00+07:00","source":"zendo-pwa","localDate":"2026-10-15"}
        """.trimIndent()
        val snapshot = ok(minimal)
        assertNull(snapshot.action)
        assertNull(snapshot.highlight)
        assertTrue(snapshot.tasks.isEmpty())
        assertTrue(snapshot.isEmpty)
    }

    @Test
    fun `files that are not snapshots are rejected with a readable sentence`() {
        assertTrue(reject("""{"format":"something-else"}""").message.contains("not a Zendo"))
        assertTrue(reject("not json at all").message.contains("not a valid JSON"))
        assertTrue(reject("").message.contains("empty"))
        val noVersion = """
            {"format":"zendo-launcher-bridge","exportedAt":"x","source":"zendo-pwa","localDate":"2026-10-15"}
        """.trimIndent()
        assertTrue(reject(noVersion).message.contains("schemaVersion"))
    }

    @Test
    fun `a newer schema is rejected and says so`() {
        val newer = validSnapshot.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")
        val rejected = reject(newer)
        assertTrue(rejected.message.contains("newer"))
    }

    @Test
    fun `a future date is rejected`() {
        val future = validSnapshot.replace("2026-10-15", "2026-10-16")
        val rejected = reject(future)
        assertTrue(rejected.message.contains("future"))
    }

    @Test
    fun `wrong field types are rejected instead of coerced`() {
        val numberTitle = validSnapshot.replace("\"title\": \"Draft outline\"", "\"title\": 12")
        assertTrue(reject(numberTitle).message.isNotEmpty())

        val tasksNotAList = validSnapshot.replace("\"tasks\": [", "\"tasks\": {")
        assertTrue(reject(tasksNotAList).message.isNotEmpty())

        val badDueDate = validSnapshot.replace("\"dueDate\": \"2026-10-15\"", "\"dueDate\": \"yesterday\"")
        assertTrue(reject(badDueDate).message.contains("dueDate"))
    }

    @Test
    fun `a repeated task id is one task, not two`() {
        val duplicated = validSnapshot.replace("\"id\": \"p2\"", "\"id\": \"p1\"")
        val snapshot = ok(duplicated)
        assertEquals(1, snapshot.tasks.size)
    }

    @Test
    fun `an absurd number of tasks is rejected`() {
        val many = (0 until 600).joinToString(",\n") {
            """{"id":"p$it","title":"Task $it"}"""
        }
        val raw = """
            {"format":"zendo-launcher-bridge","schemaVersion":1,
             "exportedAt":"2026-10-15T08:00:00+07:00","source":"zendo-pwa","localDate":"2026-10-15",
             "tasks":[$many]}
        """.trimIndent()
        assertTrue(reject(raw).message.contains("more than"))
    }

    @Test
    fun `a backup round trip restores action, lists, history and clamped settings`() {
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
            prefs = BackupPrefs(pinLimit = 99, screenTimeEnabled = true, clock24h = false),
        )

        val raw = BridgeJson.encodeBackup(contents, "0.5.0", 1_790_000_000_000L)
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
        assertEquals(6, parsed.prefs.pinLimit) // clamped to the allowed maximum
        assertTrue(parsed.prefs.screenTimeEnabled)
        assertFalse(parsed.prefs.clock24h)
    }

    @Test
    fun `backup parsing rejects other files and newer versions`() {
        val wrong = BridgeJson.parseBackup(validSnapshot) as BackupParse.Rejected
        assertTrue(wrong.message.contains("not a Zendo Launcher backup"))

        val snapshotFromBackup = BridgeJson.parseSnapshot(
            BridgeJson.encodeBackup(BackupContents(), "0.5.0", 0L),
            today,
        ) as BridgeParse.Rejected
        assertTrue(snapshotFromBackup.message.contains("not a Zendo launcher snapshot"))

        val newer = BridgeJson.encodeBackup(BackupContents(), "0.6.0", 0L)
            .replace("\"schemaVersion\":1", "\"schemaVersion\":2")
        val rejected = BridgeJson.parseBackup(newer) as BackupParse.Rejected
        assertTrue(rejected.message.contains("newer"))
    }
}
