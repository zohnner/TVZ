# Resume tile — design (approved by Zohn 2026-10-21, built on branch resume-tile)

Goal: "click back onto the last show I was binging." Up to 3 Resume tiles at the front of the dock — the most recent thing watched in each of the last 3 apps, per profile.

## Spike findings (branch `spike-resume`, commit 0b3cfee — throwaway, do not merge)
- Android's Watch Next provider (`TvContract.WatchNextPrograms`) returned 0 rows even while Peacock played → not usable on this TV.
- Media sessions via a `NotificationListenerService` + `MediaSessionManager.getActiveSessions(ComponentName)` work. Peacock published: package, PlaybackState (3 playing / 2 paused / 1 stopped, position ms, ~2 updates/s), metadata TITLE = DISPLAY_TITLE = "Double-Click" (episode title). No duration, no series name, no media id/uri → no deep link.
- Zohn ALREADY enabled notification access for `cc.zohn.tvlauncher/cc.zohn.tvlauncher.ResumeProbeListener`. Keep that exact class name in production so access isn't lost. The TV currently runs the spike build (it logs `ResumeProbe` lines while media plays).

## Approved design
1. **Recording** (`ResumeProbeListener : NotificationListenerService`): track active sessions (initial list + `addOnActiveSessionsChangedListener` + per-controller callbacks). Accumulate real playing time per session (wall time while PLAYING). When it first reaches **2 minutes**, and on pause/stop/title change (max one write per 30 s per app), write `ResumeEntry(packageName, title?, positionMs, durationMs?, watchedAt epoch ms)` for the **profile active at that time** (`LauncherPrefs.activeProfile`). One entry per app per profile. Title: TITLE → DISPLAY_TITLE → null. Duration: METADATA_KEY_DURATION if > 0. Ignore own package and the profile's "never track" set. No heavy work per position update.
2. **Storage** — per-profile JSON list in SharedPreferences + per-profile "never track" set. Pure, unit-tested helpers: `upsert` (replace same package), `visibleResumes` (drop > **7 days** old and never-tracked, newest first, take **3**), `shouldRecord(playedMs) = playedMs >= 120_000`, `resumeSubtitle(appLabel, positionMs)` ("Peacock · 12 min in", "just started" < 1 min, "1 h 5 min in").
3. **Dock** — `DockItem.Resume(entry, app)` before all other items, only if the app is still installed; key `resume:<package>`; reuse the shared DockTile chrome. Look (Zohn's pick: "App banner + overlay"): the app's banner darkened with a palette-tinted overlay, "▶ RESUME" (data font, glow color), title or app label (display font, 1 line), and a 3 dp progress bar (glow over accent @30%) when duration is known, else "12 min in". Hero: title + `resumeSubtitle` + "· press OK to resume · hold for options". Palette colors only (Zohn's two-hue rule).
4. **OK** opens the app via the normal launch path (counts + UsageLog). No deep link.
5. **Hold OK / Menu** → Dismiss · Never track <App> · Cancel.
6. **Refresh** on resume and via a SharedPreferences change listener (onStart/onStop).
7. **Manifest**: listener service with `BIND_NOTIFICATION_LISTENER_SERVICE`. No READ_EPG_DATA.
8. **Docs**: README (status, how to enable notification access, code map), DESIGN.md (tile look).

## Verification
TDD for the pure helpers; full unit suite; deploy; confirm the listener is still enabled (`dumpsys notification | grep ResumeProbeListener`, read-only); on the TV play ≥ 2.5 min in Peacock, pause, Home → Resume tile visible; hold OK → menu. Never enable/grant anything via adb and never force-stop the launcher (it disables the Home-button accessibility service).
