# Digital Wellbeing Roadmap

Principle: user-controlled, reversible, no shame, no streaks, no punishment.
Nothing here may make the device unusable or impossible to opt out of.

## Shipped (0.3.x, verified by unit tests)

- **Hide apps** (visual-only filter in this launcher; honest copy that other
  launchers/search/voice are unaffected) + timed Undo.
- **Intentional Pause**: user-picked distracting apps open a calm
  Continue/Cancel dialog instead of launching directly. Launcher-level
  pre-launch only — bypassable via any other surface, stated in Settings.
- **Access window**: optional daily window (wraps midnight) during which
  listed apps pass straight through; pure minute arithmetic (`AccessWindow`,
  fully unit-tested), read at tap time, no timers, no background work.
- **Usage summary (0.3.3, optional, numbers only)**: today's foreground
  minutes per app from `UsageStatsManager`, behind `PACKAGE_USAGE_STATS`
  special access granted in system Settings and revocable there. Explicit
  Load/Refresh taps only — no polling, no background work, no enforcement.
- **Indirect system links**: usage-access page, system Settings (for OS Focus
  Mode / Digital Wellbeing, which stock Android exposes no direct deep-link
  for), and the existing connectivity/display/sound/wallpaper pages.

## Approach comparison (researched, not implemented)

| Approach | What it gives | Verdict |
| --- | --- | --- |
| Native Digital Wellbeing / Focus Mode links | reliable OS-enforced focus | next: Indirect shortcuts to the system screens (no permission, no risk) |
| `UsageStatsManager` summaries | screentime numbers | possible, needs `PACKAGE_USAGE_STATS` + special-access onboarding; numbers only, no enforcement |
| `AccessibilityService` gating | true launch interception | **rejected for now**: surveillance-grade capability, heavy trust + review cost, contradicts the privacy stance; only with explicit justification, purpose limitation, and a visible off-switch |
| Device Owner / MDM suspension | real blocking | deployment-only (managed devices), not a consumer launcher feature |
| Chrome website blocking | per-site control | NOT achievable by gating the Chrome app; would need separate extension/DNS story — never claim otherwise |

Key constraint honored throughout: usage access ≠ blocking power, and
gating an app ≠ gating its websites.

## Next priorities (P2, in order)

1. ~~Indirect Focus Mode / Digital Wellbeing shortcuts in Settings~~ — done
   (0.3.3): usage-access deep-link + system Settings entry with guidance.
2. ~~Optional local usage summary behind `PACKAGE_USAGE_STATS`~~ — done
   (0.3.3): numbers-only, explicit loads, one-tap revoke path in the same
   system page.
3. Nothing access-granting without a dedicated privacy review and doc update
   (`privacy-and-permissions-audit.md`, this file, Settings copy).

## Explicit non-goals

Streaks, guilt copy, lockouts the owner cannot disable, password/content
collection, hidden monitoring, URL interception claims.
