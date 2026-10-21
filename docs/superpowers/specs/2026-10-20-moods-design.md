# Moods Through the Day + Zohn's Sunset Grid — Design

Date: 2026-10-20 · Status: approved in brainstorm, pending written-spec review
Sub-project 1 of "next-level personalisation" (order: Moods → Game day → Habits → On-TV customization).

## Goal

The launcher's look follows the real day. Each profile has four moods (morning, day, evening,
night) that blend smoothly, timed by Kansas City's sunrise and sunset. Night is a low-glare dark
mode for both profiles. Zohn's profile gets a new base look ("Sunset grid") replacing the
baroque gold theme. Sydney's paintings change with the time of day. A short greeting appears on
wake. Launch history starts being logged for the later Habits sub-project.

Out of scope here: game-day mode (next spec; this one only adds the override slot), habit-based
ordering, any settings UI.

## Design rules (from Zohn)

- One theme, consistently applied. Zohn's profile uses **two hues only: purple + orange** on
  dark neutrals. Nothing from Sydney's look leaks into his, and vice versa.
- Game days must not add clutter: team colors replace purple/orange in the same palette slots;
  layout, fonts and effects stay the same.
- Performance rules in DESIGN.md still hold: ≤ 2 full-screen layers per frame, no per-frame
  scene redraws, motion pauses while the remote is in use.

## 1. Timing — `SunCalc` + `MoodClock`

- `SunCalc`: NOAA solar algorithm. Inputs: date, latitude/longitude. Outputs: sunrise, sunset,
  sun elevation at a given instant. Pure Kotlin, no Android dependencies, offline.
- Location: Kansas City, MO (39.10, −94.58), stored next to `WEATHER_CITY` as one setting.
- Phase boundaries (each is the **midpoint** of a 45-minute blend):

  | Phase | Starts | e.g. 2026-10-20 (sunrise ≈ 7:30, sunset ≈ 6:40) |
  |---|---|---|
  | Morning | sunrise − 30 min | 7:00 am |
  | Day | sunrise + 3 h | 10:30 am |
  | Evening | sunset − 1 h | 5:40 pm |
  | Night | sunset + 2.5 h | 9:10 pm |

- `MoodClock` exposes `phase`, `nextPhase`, and `blend` (0–1 toward `nextPhase`; 0 outside
  blend windows) plus the current sun elevation.
- Ticks every 2 minutes inside a blend window; otherwise sleeps until the next window. Recomputes
  at local midnight, on launcher start, and on `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED`.
- Debug: holding Menu on the profile name toggles "fast day" (a full day in 2 minutes) for
  on-TV review. Not shown anywhere else.

## 2. Palettes

`LauncherPalette` keeps its fields and gains scene fields used only by Zohn's Sunset grid:
`sky1, sky2, sky3, horizon, ground, sunTop, sunMid, sunBottom, gridH, gridV, starAlpha,
gridAlpha, glowStrength` (glowStrength 0 = no focus bloom, used at night).

Effective palette = `blend(phasePalette[phase], phasePalette[nextPhase], blend)` (per-channel
color lerp, numeric lerp for alphas), unless a `ThemeOverride` is set (section 6).

### Zohn — Sunset grid (purple + orange)

Fonts: **Orbitron** (display: name, clock, hero) + **JetBrains Mono** (data lines: date,
weather, hints). Both OFL, bundled in `res/font/`.

| Phase | sky1 / sky2 / sky3 / horizon / ground | Sun (top/mid/bottom) | Accent 1 (orange) / Accent 2 (purple) / text |
|---|---|---|---|
| Morning "Sunrise" | #1C0F3A / #5A2C7A / #C9668A / #FFB27A / #120821 | #FFE0A3 / #FF9E5E / #E0567A, just cresting | #FF8A3D / #D8B4FE / #FFF1E6 |
| Day "High sun" | #2A0E55 / #6B2A8F / #B9417F / #FF7A3D / #14061F | #FFD07A / #FF7A3D / #D6336C, high | #FF8A3D / #D8B4FE / #FFF1E6 |
| Evening "Sunset" | #170730 / #4A1260 / #9C1F5E / #FF5E3A / #100418 | #FFB347 / #FF5E3A / #C2185B, half-set | #FF6B35 / #C4A1F5 / #FFE9DC |
| Night "Afterglow" | #05030D / #120826 / #24103D / #3A1A55 / #06030C | none; stars at 0.9 | #B26A3D / #8E7AB8 / #C9BFD6 |

Palette slot mapping for Zohn: `accent` = purple (frames, date, lines), `glow` = orange (focus
frame + glow, profile name), `text` as above, `surface` = ground at 85–90% alpha, `background`
= ground. Night: glowStrength 0, grid alpha 0.35.

### Sydney — Water Lilies (unchanged base, four moods)

| Phase | background / surface / accent / glow / text | Painting subjects |
|---|---|---|
| Morning "Dawn mist" | #E6E3EE / #F3F0F2 / #7C9A8A / #9B86C8 / #2A3440 | sunrise, morning |
| Day (today's look) | #DCE7EE / #EEF2EC / #6E8F7A / #8C7BC0 / #22313A | garden, water lilies, river |
| Evening "Sunset on the pond" | #F0DCCB / #F6E9DE / #9A7B5C / #B0679A / #3A2A2E | sunset, evening |
| Night "Evening pond" | #10202A / #1B3038 / #A99BD0 / #EBA9BE / #F4EFE6 | night, moonlight, nocturne |

Secondary (lily pink) stays #E6A3B4 except at night (#8FB59A sage). Font stays Playfair Display.

## 3. Backgrounds

### Zohn — `SunsetScene`
- Rendered on the CPU into a **960×540** bitmap and stretched to the screen (grid lines stay
  crisp; ~2 MB; ~15 ms). Re-rendered only on MoodClock ticks, never per frame.
- Scene bitmap: sky gradient → stars (alpha by phase) → striped sun → horizon line → ground →
  soft dark veil behind the hero area. The horizon sits at the divider's height (the line between
  hero and dock), so the divider and horizon are one line.
- The perspective grid floor is a **separate** pre-rendered layer (below the horizon only,
  fading toward it), so it can scroll without re-rendering the scene.
- Sun: positioned low-right (center x ≈ 76% of width), never behind the hero text. Its height
  maps from real sun elevation: below the horizon line when elevation < 0 (clipped), rising
  to its high position by mid-morning. Hidden at night.
- Motion: the grid floor scrolls slowly toward the viewer as a translated pre-rendered layer
  (same pattern as today's drift): pauses on any key press, resumes after 2 s idle, static at
  night. Replaces the gold drift.
- The blurred focused-app backdrop is **removed** for Zohn (clutter + an extra layer).
- Layers per frame: scene (1) + moving grid layer (1) = 2.

### Sydney — paintings by phase
- Four paintings per day, one per phase, chosen from the Impressionist list by phase subjects
  (table above) via the Art Institute of Chicago search. Fallback chain: subject match → any
  painting by the day's painter → any cached painting → pond placeholder.
- Each crossfades in at its phase midpoint; the art card + fact follow the visible painting.
- Fetched lazily per phase (first time a phase is reached each day), cached (16-image cap).
- Night: the mist layer becomes the dark Evening-pond veil; painting effectively ~55% visible.
- The idle gallery uses the current phase's subjects.

### Both — night low-glare
No focus bloom (glowStrength 0), lower text contrast per the night palettes, nothing bright.

## 4. Greeting (on wake only)

- Trigger: the launcher resumes after the screen was off / screensaver ran for ≥ 30 minutes
  (track `ACTION_SCREEN_OFF` time), or on profile switch. Not on returning from an app.
- Shows in the hero for ~4 s, then fades back to the focused app. Any key skips it.
- Text: Morning "Good morning, {name}", Day "Good afternoon, {name}", Evening "Good evening,
  {name}", Night "Late night, {name}". Profile display font (Orbitron for Zohn, Playfair italic
  for Sydney).

## 5. Usage log (for the Habits sub-project)

- `UsageLog` appends one line per launch from the dock: ISO timestamp, weekday, hour, phase,
  profile id, package. File: `files/usage.log`, trimmed to the last 90 days on start.
- Failures are caught and logged; they never block launching an app. No UI uses it yet.

## 6. Override slot (for Game day)

`ThemeOverride(palette: LauncherPalette, heroLine: String?, until: Instant)` held in
MainActivity state, null for now. When set, it replaces the mood palette (no blending) and its
`heroLine` shows above the hero title. Game day (next spec) will fill it.

## 7. Units and files

| File | Purpose |
|---|---|
| `SunCalc.kt` | Solar math (pure) |
| `MoodClock.kt` | Phase + blend + sun elevation, ticking |
| `Moods.kt` | Phase palettes per profile, `blend()` |
| `SunsetScene.kt` | Zohn's scene renderer |
| `Greeting.kt` | Wake detection + greeting text |
| `UsageLog.kt` | Launch history file |
| `Art.kt` (changed) | Per-phase subject search + fallback chain |
| `Background.kt` (changed) | Zohn → SunsetScene; Sydney → painting by phase; night veil |
| `ui/theme/Palette.kt` (changed) | New fields, Zohn's new fonts |
| `Profiles.kt` (changed) | Profiles point at their phase palettes |
| `MainActivity.kt` (changed) | Owns MoodClock, effective palette, override, wake tracking |
| `res/font/orbitron.ttf`, `jetbrains_mono.ttf` | Zohn's fonts |

Cinzel, the baroque palette and the ornament divider are removed from Zohn's profile (kept in git
history). The ornament divider becomes the horizon line of the scene for Zohn; Sydney keeps hers.

## 8. Error handling

- Offline: sun math unaffected; Sydney uses cached/placeholder art; weather line simply missing.
- Clock/timezone changes: MoodClock recomputes on broadcast.
- Bitmap render failure: keep the previous scene; log.
- Art fetch failure: fallback chain (section 3).

## 9. Testing

- Unit (JVM): SunCalc vs published Kansas City sunrise/sunset for both solstices, both
  equinoxes and the day of the November DST change (±2 min). MoodClock phase/blend at chosen
  instants (inside/outside blend windows, across midnight). Palette `blend` at t = 0, 0.5, 1.
- On TV: fast-day debug mode; screenshot each phase for both profiles; `dumpsys gfxinfo` during
  navigation stays within today's numbers (GPU ≤ ~9 ms, ≤ 2 full-screen layers).
- Docs: update README (status, code map), DESIGN.md (new Zohn look, moods), RULES.md amendment
  (sports promoted into scope for Zohn's profile; game-day spec to follow).
