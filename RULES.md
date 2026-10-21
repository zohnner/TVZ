# TV Launcher — Rules (saved 2026-10-20, Zohn's words / decisions)

These are standing rules for this project. Any agent (G-Unit, Claude Code, future sessions) follows them.

## Ownership
1. **Zohn writes the code himself.** Agents provide scaffold, spec, review, and debugging help — not a finished launcher handed to him, unless he explicitly asks for a specific piece to be written.
2. Target is **his current Google TV / Android TV** — no external Pi/mini-PC box, no flashing the TV, no Projectivy / third-party launcher as the end product.

## Install route
3. Install only via the Google TV experimental/developer route: Developer Mode (Settings > System > About > Android TV OS Build x7), USB/Wireless debugging ON, Unknown sources allowed for the installer used.
4. Install with `adb install -r app-debug.apk` or USB + file manager. Never install over / disable anything system-critical to test.

## The manifest rule (non-negotiable)
5. MainActivity must declare BOTH intent filters, always:
   - `MAIN + LEANBACK_LAUNCHER` — discoverable as a TV app
   - `MAIN + HOME + DEFAULT` — selectable as the default Home launcher
   Missing either one is a bug, even if the app installs and runs.

## Safety / reversibility
6. **Never disable, uninstall, or `pm disable` the stock Google TV launcher** until Zohn's launcher has survived a clean reboot and Home-press test. Stock launcher stays installed underneath at all times.
7. Revert must always be one step: switch Home app back to stock, or `adb uninstall` Zohn's package. No change may break that revert path.
8. No brick-risk moves: no bootloader work, no root, no system-partition changes.

## TV UX rules
9. **Every interactive element must be D-pad focusable and visibly focused.** No touchscreen, no mouse, no hover. If it can't be reached with arrows + OK + Back, it doesn't exist.
10. Landscape, 10-foot UI: large type/targets, readable from the couch, focus state obvious at a distance.
11. Home must work cold: after reboot, pressing Home lands in Zohn's launcher. Test reboot + Home on every build before calling it done.

## App-discovery rule
12. Discover apps via `PackageManager` `LEANBACK_LAUNCHER` query, launch via `getLeanbackLaunchIntentForPackage()` (fallback to `getLaunchIntentForPackage()`). Don't hardcode a list of package names as the primary mechanism.

## Scope discipline
13. V1 is a launcher: app grid/rows, launch apps, set as Home. No ad-detection, PiP, music visualizer, or SportStrata widgets in V1 unless Zohn explicitly promotes them — those were Oct 11 brainstorm ideas, not V1 scope.

## Amendments (2026-10-20, Zohn's decisions during the build session)
These supersede the original rules above where they conflict.

- **Rule 1 (ownership):** Zohn said "forget that, full speed ahead" — agents may now write and install the code. Zohn still decides the look: question him on style, theme colors and effects before designing any new UI.
- **Rule 13 (scope):** V1 is done (installs, launches apps, is Home, survives reboot). Zohn promoted these into scope: HDMI inputs, hide apps, weather, quick actions, most-used ordering, instant (cached) loading. Smoothness on this TV is a standing priority. Ad-detection, PiP, visualizer, SportStrata widgets are still out unless promoted.
- **Rules 6/7 (Home button):** Google TV ignores the HOME role for the Home key. Approved fix: an accessibility service in this app (`HomeRedirectService`) that redirects from the stock launcher's home screen. The stock launcher stays installed and enabled. Revert stays one step: uninstall, or turn the service off in Accessibility.
- **Rule 6, clarified:** "Never disable" now has no exception. Never disable/uninstall the stock launcher, even after the reboot test passed.
- **Repo:** the Android project lives in this repo (TVZ). An older copy at `C:\Users\zohnw\AndroidStudioProjects\tvz` is stale; don't edit it.
