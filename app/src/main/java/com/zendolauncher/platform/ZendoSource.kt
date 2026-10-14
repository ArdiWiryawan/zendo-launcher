package com.zendolauncher.platform

import com.zendolauncher.model.ZendoToday

/**
 * Integration boundary for Zendo's daily planning block.
 *
 * The home screen renders before this resolves and renders nothing when it returns
 * null, so the launcher never depends on Zendo being reachable — or installed.
 *
 * Unshipped dependency, named exactly so the next implementation does not have to
 * rediscover it:
 *   1. Read path — Supabase PostgREST, `GET {SUPABASE_URL}/rest/v1/zendo_state`
 *      (queried with the anon key in `apikey`; the row is owner-scoped by `user_id`).
 *   2. Auth — the request needs that user's access token as `Authorization: Bearer …`.
 *      Zendo's own session is a Supabase password sign-in, so the launcher would have
 *      to either re-implement that sign-in or call a backend endpoint that mediates it.
 *   3. Row shape — the launcher maps the row into [com.zendolauncher.model.ZendoDayPlan]
 *      and then through `TodaySelector`; no other table is read.
 *
 * None of that is shipped and none of it is invented here. It would need the network
 * permission and a stored credential, so it is deliberately absent from a launcher whose
 * manifest requests nothing. [UnavailableZendoSource] is the shipped stub: it returns
 * null, always, and the launcher is complete without it.
 * See docs/zendo-integration.md for the required backend change.
 */
interface ZendoSource {
    /** Today's block, or null when unavailable. Must never throw or block the UI. */
    suspend fun load(): ZendoToday?
}

/** The shipped default: the launcher is fully usable with no Zendo connection. */
object UnavailableZendoSource : ZendoSource {
    /** Stubbed, not silently failing: the caller already renders nothing for null. */
    override suspend fun load(): ZendoToday? = null
}
