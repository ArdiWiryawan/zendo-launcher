# Zendo integration audit

## Verdict

**No.** Zendo exposes **no HTTP/REST/GraphQL API for planning data.** The only
serverless functions are two Bayar GG payment endpoints. The one real, supported
integration surface is **Supabase PostgREST against the `zendo_state` table**,
which the web app already uses for optional cross-device sync. A separate client
can read that row only by authenticating as the same Supabase user (stock
Supabase email/password against the project URL + anon key), because RLS restricts
the row to `auth.uid()::text = id`. There is no token, share link, export
endpoint, or public read path. The payload is one opaque JSON blob, not a typed API.

## What exists today

None of the paths cited in this document exist in this repository: `supabase_init.sql`, `src/`,
`api/`, `vercel.json`, and `app/src/main/java/app/vercel/zendo_focus/twa/` are all paths in the
separate Zendo web app repository, which was read for this audit and is not part of this tree —
the only `app/` here is this launcher's own Android module.

### 1. No planning-data API

- `api/` holds exactly `bayargg-checkout.ts`, `bayargg-webhook.ts` + their tests
  (`bayargg-webhook.security.test.ts`, `packPrices.test.ts`) — payments only.
- Only `fetch(` in `src/`: `src/screens/JournalPacks.tsx:681` →
  `fetch("/api/bayargg-checkout", ...)`. No axios, no GraphQL.
- `vercel.json:2-4` rewrites `/api/(.*)` → `/api/$1` and everything else to
  `/index.html`. No API surface declared.
- `docs/` (18 files: `sync-conflict-policy.md`, `product-vocabulary.md`, audits,
  specs) contains no backend or endpoint spec.
- No Next.js `app/` router; `app/` here is the Android TWA wrapper
  (`app/src/main/java/app/vercel/zendo_focus/twa/`).

### 2. What the Supabase integration does

Single module `src/lib/supabase.ts`. Client built from URL + anon key only
(`src/lib/supabase.ts:20-31`); if either is unset the client is `null` and every
call degrades to a no-op (`:10-11, 41-43`).

Every `.from('` call in `src/` (non-test) is one of three lines:

| Location | Table | Columns | Op |
|---|---|---|---|
| `src/lib/supabase.ts:80-86` | `zendo_state` | `select('state_json').eq('id', uid).single()` | read own row |
| `src/lib/supabase.ts:95-97` | `zendo_state` | `upsert({ id: uid, state_json, updated_at })` | write own row |
| `src/lib/supabase.ts:109-113` | `zendo_purchases` | `select('pack_id')` | read all (premium unlock) |

No `.rpc(` anywhere. Row id = the user's auth uid (`:66-71`, `myRowId()` →
`data.session?.user?.id ?? null`); no uid → read returns `{}`, write returns early.

**Payload.** `setState()` receives the entire Zustand store (`src/main.tsx:31, 44`:
`useMonkStore.getState()`), stored as `JSON.stringify(state)` (`:94`) and read back
with `JSON.parse(data.state_json)` (`:86`). So `state_json` is the whole app state
serialized as a **JSON string**; top-level keys are `MonkMVPState` fields
(`src/types/app.ts:710-772`).

**Mechanics** (`src/main.tsx`): render first, sync in background (`:19-24, 129`);
push debounced 800 ms on store change (`:103-109`); pull on auth change and at
boot with a 3 s timeout race (`:62-65`) and a localStorage backup before applying
remote (`:71`); last-write-wins merge via `mergeRemoteState`
(`src/lib/syncMerge.ts:117`).

### 3. Exact data shape for the Today block

Inside `state_json`, top-level keys = `MonkMVPState` (`src/types/app.ts:710-772`):

- `dayPlans: DayPlan[]` — `src/types/app.ts:725`
- `practices: Practice[]` — `src/types/app.ts:720`
- `practiceLogs: PracticeLog[]` — `src/types/app.ts:721`
- `activeSeason: Season | null` — `src/types/app.ts:713`

**`DayPlan`** — `src/types/app.ts:337-353`:

```ts
export type DayPlan = {
  id: string;
  seasonId: string;
  weeklyPlanId: string;
  date: DateOnlyString;        // "YYYY-MM-DD"
  dayType: DayType;            // "goal" | "rest"
  goalId?: string;
  mainAction?: string;
  highlight?: string;
  agenda?: string[];
  energyLevel?: EnergyLevel;
  status: DayStatus;
  planningCompleted?: boolean;
  timeBlocks?: TimeBlock[];
  createdAt: ISODateString;
  updatedAt: ISODateString;
};
```

- **Daily Highlight** = `DayPlan.highlight` (`string | undefined`), written by
  `setTodayHighlight` (`src/store/useMonkStore.ts:1554-1558`), rendered at
  `src/screens/TodayScreen.tsx:846, 953`.
- **Main Action** = `DayPlan.mainAction` (`string | undefined`); completed via
  `completeTodayMainAction` (`src/store/useMonkStore.ts:929-944`).
- **Next Agenda item** = `DayPlan.agenda[0]` (`string[] | undefined`), in entry
  order; rendered `src/screens/TodayScreen.tsx:1576-1582`. Items are bare strings
  — no time, no done-flag.
- **Practices** = `Practice` (`src/types/app.ts:239-256`): `id`, `seasonId`,
  `name`, `goalId?`, `weeklyTargetCount: number`, `cue?`,
  `status: "active" | "paused" | "archived"`, `createdAt`, `updatedAt`.

**Today's plan resolution** — `src/store/selectors.ts:34-51` `selectTodayPlan`:
filter `dayPlans` where `seasonId === activeSeason.id && date === today`, pick the
newest via `isNewerDayPlan` (tie-break for cross-device duplicate rows). Any client
must replicate this filter.

**"Selected Practices" is UNVERIFIED.** No field selects practices for a day.
`Practice` has only `status` and `weeklyTargetCount`; per-day completion lives in
separate `PracticeLog` records (`src/types/app.ts:264-271`: `id`, `practiceId`,
`seasonId`, `date`, `createdAt`, `updatedAt`). Any "selected practices" definition
would be invented by the launcher, not read from Zendo.

### 4. Auth

- Sign-in is Supabase **email + password**, not magic link, not OAuth:
  `signInWithPassword({ email, password })` (`src/components/LoginScreen.tsx:25`),
  `signUp({ email, password })` (`src/components/SignupScreen.tsx:27`). Grep for
  `signInWithOtp` / `signInWithOAuth` / `resetPasswordForEmail` in `src/`: nothing.
- The flow is stock Supabase Auth, **not custom**, so a separate Android client
  could obtain a user access token via the Supabase Auth password grant with the
  same project URL + anon key, if the project permits it.
- Credentials needed: `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY` (both
  client-side by design) plus a user access token from that user signing in. No
  service-role key is in the client or needed to read one's own row.
- Whether the live project has email confirmation on, or allows the password grant
  for non-web clients, is UNVERIFIED (project settings, not in the repo).

### 5. Row Level Security

Only schema file: `supabase_init.sql` (the `.claude/worktrees/*/supabase_init.sql`
hits are copies of the same file). Verbatim, `supabase_init.sql:7-30`:

```sql
create table if not exists zendo_state (
  id text primary key,
  state_json jsonb default '{}'::jsonb,
  updated_at timestamptz default now()
);

alter table zendo_state enable row level security;

-- Drop existing policy if exists before recreating
drop policy if exists "Allow owner CRUD" on zendo_state;
drop policy if exists "Allow anon read/write" on zendo_state;
drop policy if exists "Allow authenticated CRUD" on zendo_state;

-- Owner-only access. id = auth.uid()::text matches text column with UUID auth id.
create policy "Allow owner CRUD"
  on zendo_state
  for all
  to authenticated
  using (auth.uid()::text = id)
  with check (auth.uid()::text = id);

-- Delete the legacy shared 'global' row so it can never again be written by
-- clients and read back into users' state.
delete from zendo_state where id = 'global';
```

Payment table, `supabase_init.sql:38-58`: `zendo_purchases (id text primary key,
pack_id text not null, amount int not null, paid_at timestamptz)`, RLS
`create policy "Allow authenticated read purchases" ... for select to
authenticated using (true)`.

Consequence: the launcher's only legal read of `zendo_state` is
`GET /rest/v1/zendo_state?id=eq.<uid>&select=state_json` with the user's own
access token. Anon key alone yields zero rows.

## Data contract available to the launcher

| Launcher field | Source in `state_json` | Type | Confidence |
|---|---|---|---|
| Daily Highlight | `dayPlans[] → highlight` | `string \| undefined` | VERIFIED (`src/types/app.ts:345`) |
| Main Action | `dayPlans[] → mainAction` | `string \| undefined` | VERIFIED (`src/types/app.ts:344`) |
| Next Agenda item | `dayPlans[] → agenda[0]` | `string[] \| undefined` | VERIFIED (`src/types/app.ts:346`) |
| Practices | `practices[] → name`, `status` | `Practice[]` | VERIFIED (`src/types/app.ts:239-256`) |
| "Selected" practices for today | — | — | UNVERIFIED — no such field exists |
| Row / column | `zendo_state.id`, `zendo_state.state_json` | `text`, `jsonb` | VERIFIED (`supabase_init.sql:7-11`) |

## Recommended integration boundary for the launcher

One interface, one nullable result. This is the shipped shape, not a proposal:
`platform/ZendoSource.kt` and `model/ZendoToday.kt` declare exactly these two types.

```kotlin
interface ZendoSource {
    /** Null = Zendo unavailable/not configured/signed out. Never throws to UI. */
    suspend fun load(): ZendoToday?
}

data class ZendoToday(
    val highlight: String? = null,
    val mainAction: String? = null,
    val nextAgenda: AgendaItem? = null,   // title, and a time only if Zendo has one
    val practices: List<String> = emptyList(),   // names, active practices only
)
```

- Home screen renders immediately from local state; the Today block starts empty
  and is filled by a coroutine. **Never** await `load()` on the main thread or in
  a blocking `onCreate` path.
- `load()` returns `null` on: no URL/key, no signed-in user, any HTTP/auth/parse
  failure, or offline with no cache. Callers render nothing when null — no error
  UI, no placeholder implying Zendo is broken.
- Cache the last good snapshot in the launcher's own storage. Freshness rule:
  **serve cache immediately, refresh in background, treat cache as stale after
  15 minutes**; a stale cache still renders but never blocks.
- **Implementable today against verified facts:** fetch `zendo_state` by
  `id = <uid>` with `select=state_json` using URL + anon key + a user access
  token; parse `dayPlans`, select today's row per `selectors.ts:34-51`
  (`seasonId == activeSeason.id && date == today`, newest wins); read `highlight`,
  `mainAction`, `agenda[0]`, and `practices` filtered to `status == "active"`.
- **Requires a backend/product decision:** obtaining that access token. Two honest
  options — (1) the launcher hosts its own Supabase email/password sign-in (stock
  Supabase Auth REST, no Zendo backend change, but the launcher then holds the
  user's Zendo credentials); or (2) **backend change:** add an authenticated read
  endpoint, e.g. `GET /api/zendo-today` keyed by a launcher token, returning a
  typed projection `{ highlight: string|null, mainAction: string|null,
  agenda: string[], practices: {id, name}[] }` — so the launcher never handles
  Zendo passwords and never parses the whole blob. This endpoint does not exist.
- If neither is accepted, ship the feature **absent**, not as a best-effort read of
  a private table.

### The selector half already ships, with no source behind it

`core/TodaySelector.kt` (`ZendoDayPlan`, `TodaySelector.select`) already encodes
the row-selection rule above — today's `dayPlans` entry, newest wins, blank fields
dropped — and is covered by `core/TodaySelectorTest.kt` (6 tests). It has **no
production caller**: no `ZendoSource` implementation exists, and
`platform/ZendoSource.kt` ships only `UnavailableZendoSource`, whose `load()`
returns null. It is code prepared for a source, not a working feature, so its
presence is not evidence that the Today block works; `ZendoToday.practices` is
never populated by anything.

## Explicit non-goals

- Must not **write** to `zendo_state` or `zendo_purchases` — no upsert, update, or
  insert. Read-only.
- Must not **duplicate** Zendo records: no local Goals/Agenda/Practices store, no
  editable copy, no re-upload. Only a read-only snapshot plus a TTL cache.
- Must not **invent** endpoints, tables, columns, or fields beyond those above (no
  "selected practices" field, no per-agenda times, no agenda done-flags).
- Must not **block** the home screen on network, auth, or Zendo availability; no
  gating spinner, no retry loop on the UI path.
- Must not use a Supabase service-role key, nor request one.

## Unverified items

- Live DB state: whether the deployed project matches `supabase_init.sql` exactly.
- Whether `state_json` is a JSON string or object in the live column; the client
  reads it as a string (`src/lib/supabase.ts:86`), but the DDL default is
  `'{}'::jsonb` (`supabase_init.sql:9`).
- Whether the live project has email confirmation enabled, and whether the
  password grant is allowed for non-web clients.
- Actual `VITE_SUPABASE_URL` / `VITE_SUPABASE_ANON_KEY` values (`.env.local` out of
  scope; `.env.example` ships them empty).
- Semantics of "selected practices" — no such concept exists in the types.
- Whether planning data is reachable by any route other than the owner-authed
  `zendo_state` row — none found, but the live deployment was not probed.
- The Today block's status in the launcher: the selector is written and tested but
  has no production caller, so nothing reads Zendo today. See the boundary section.
