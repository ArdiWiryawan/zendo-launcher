package com.zendolauncher.platform

import com.zendolauncher.model.ZendoToday

/**
 * Integration boundary for Zendo's daily planning block.
 *
 * The home screen renders before this resolves and renders nothing when it returns
 * null, so the launcher never depends on Zendo being reachable — or installed.
 *
 * Zendo exposes no planning HTTP API today. The only verified read path is Supabase
 * PostgREST on the `zendo_state` table, which is owner-only and needs the user's
 * access token. A real implementation therefore has to either host Zendo's
 * Supabase password sign-in itself or call a new backend endpoint. Neither is
 * shipped here: inventing an endpoint would be worse than showing nothing.
 * See docs/zendo-integration.md for the required backend change.
 */
interface ZendoSource {
    /** Today's block, or null when unavailable. Must never throw or block the UI. */
    suspend fun load(): ZendoToday?
}

/** The shipped default: the launcher is fully usable with no Zendo connection. */
object UnavailableZendoSource : ZendoSource {
    override suspend fun load(): ZendoToday? = null
}
