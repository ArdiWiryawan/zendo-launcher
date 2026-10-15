package com.zendolauncher.core

/**
 * A persistent user-authored highlight. It stays until the user edits,
 * archives or deletes it — never auto-erased at midnight. [origin]/[sourceId]
 * record a Zendo snapshot import the same way tasks do.
 */
data class Highlight(
    val id: String,
    val title: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPrimary: Boolean = false,
    val archived: Boolean = false,
    val origin: String = Origins.LOCAL,
    val sourceId: String? = null,
) {
    companion object {
        fun newId(): String = "h_" + java.util.UUID.randomUUID().toString().replace("-", "")

        /** Repairs a list: ids unique, titles non-blank, one primary among visible. */
        fun sanitizeAll(items: List<Highlight>): List<Highlight> {
            if (items.isEmpty()) return emptyList()
            val seen = HashSet<String>()
            val repaired = items.mapNotNull { h ->
                val id = h.id.takeIf { it.isNotBlank() && seen.add(it) } ?: return@mapNotNull null
                val title = h.title.trim()
                if (title.isEmpty()) return@mapNotNull null
                h.copy(
                    id = id,
                    title = title,
                    origin = Origins.normalize(h.origin),
                    sourceId = h.sourceId?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            if (repaired.isEmpty()) return emptyList()
            // Exactly one primary among unarchived; first wins, else first unarchived.
            val visible = repaired.filter { !it.archived }
            val primaryId = visible.firstOrNull { it.isPrimary }?.id
                ?: visible.firstOrNull()?.id
            return repaired.map { it.copy(isPrimary = it.id == primaryId && !it.archived) }
        }
    }
}

fun List<Highlight>.primary(): Highlight? = firstOrNull { !it.archived && it.isPrimary }
    ?: firstOrNull { !it.archived }

fun List<Highlight>.visible(): List<Highlight> = filter { !it.archived }

fun List<Highlight>.archivedList(): List<Highlight> = filter { it.archived }
