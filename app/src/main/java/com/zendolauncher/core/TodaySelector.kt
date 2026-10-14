package com.zendolauncher.core

import com.zendolauncher.model.AgendaItem
import com.zendolauncher.model.ZendoToday
import java.time.LocalDate

/**
 * A day's planning entry as Zendo stores it in `zendo_state.state_json.dayPlans[]`.
 * Only the fields the launcher reads are modelled; see docs/zendo-integration.md.
 */
data class ZendoDayPlan(
    val date: String,
    val highlight: String? = null,
    val mainAction: String? = null,
    val agenda: List<String> = emptyList(),
)

/**
 * Picks today's planning block out of Zendo's state. Pure so it is unit-tested on
 * the JVM and cannot block the home screen.
 *
 * Zendo's `agenda` is a plain list of strings with no time attached, so an agenda
 * item is shown without a time rather than inventing one. "Selected practices" is
 * not a field Zendo exposes, so practices are left empty instead of guessed.
 *
 * A stored day plan carries one agenda string and no task list, so this fills
 * `nextAgenda` only and leaves `tasks` and `upcoming` at their empty defaults. Those
 * fields exist on [ZendoToday] for a source that can supply them; filling them here
 * would be inventing data this source does not have.
 */
object TodaySelector {

    fun select(plans: List<ZendoDayPlan>, today: LocalDate): ZendoToday? {
        val isoDate = today.toString()
        val plan = plans.lastOrNull { it.date == isoDate } ?: return null
        val today = ZendoToday(
            highlight = plan.highlight?.takeIf { it.isNotBlank() },
            mainAction = plan.mainAction?.takeIf { it.isNotBlank() },
            nextAgenda = plan.agenda.firstOrNull { it.isNotBlank() }?.let { AgendaItem(title = it) },
        )
        return if (today.isEmpty) null else today
    }
}
