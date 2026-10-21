# Zohn TV — Design (decided 2026-10-20)

Two profiles share one layout: **Zohn** (Sunset grid, below) and **Sydney** (Monet's Water Lilies,
see the end). Colors come from `LocalPalette` — never hard-code a profile's colors in UI code.
Each profile has four moods (morning, day, evening, night) that follow Kansas City's sunrise and
sunset; see "Moods" below.

**Theme (Zohn):** Sunset grid. **Two hues only: purple + orange** on dark neutrals. Game days will
swap team colors into the same slots; nothing is added.
Any new visual feature: ask Zohn about style, colors and effects **before** building it.

## Layout: Hero + dock
- Top bar, left: `ZOHN` wordmark.
- Top bar, right: clock, date, weather line (°F), then a row of round quick-action icons:
  Inputs (only if the TV exposes HDMI inputs), Wi-Fi, Bluetooth, Settings. Reached with Up from the dock.
- Hero: focused app's name, large (or the focused quick action's name), with a hint line.
- Divider: for Zohn it is the scene's horizon line; Sydney keeps a thin rule with a center diamond.
- Dock: one horizontal row of app banners, **most-used first** (launches from Zohn TV), then A–Z.
  Settings lives in the quick actions, not the dock.
- Hold OK (or press Menu) on a tile: menu with Hide / Unhide, App info, Show hidden apps.
- **Input tiles** share the dock and its ordering (counts keyed `input:<id>`). Same size, focus scale, glow and border as app tiles; content is a large outlined icon (`palette.accent`, focused `palette.text`), the title in `palette.display` and the port in `palette.data`, on `palette.surface`. Hold OK / Menu: Rename (kind list, typed name, automatic) / Hide. Ports with no CEC device, no connection and no user name stay out of the dock but remain in the Inputs menu.
  Hidden apps, when shown, are dimmed.
- **Resume tiles** (up to 3) come before everything else in the dock, newest first; key `resume:<package>`. Same chrome as app tiles. Look (Zohn's pick "App banner + overlay", 2026-10-21): the app's banner under a `palette.background` veil at 66%, then bottom-left "▶ RESUME" (`palette.data`, `palette.glow`), the episode title or app label (`palette.display`, one line), and a 3 dp progress bar (`palette.glow` over `palette.accent` at 30%) when the app gives a duration, else "12 min in" (`palette.data`). Hero: title, then "Peacock · 12 min in · press OK to resume · hold for options". Hold OK / Menu: Dismiss / Never track <App> / Cancel. Palette colors only.
- Menus: dark panel in the profile's surface color, thin accent border, display-font title, glow-highlighted focused row.

## Zohn: Sunset grid
- Fonts: **Orbitron** (display: name, clock, hero) + **JetBrains Mono** (data lines: date, weather, hints),
  both OFL, in `res/font/`.
- The sun tracks the real sun elevation. It sits in the right corner (center ~85% of width, diameter 15% of width) and is capped: its top edge never rises above 40% of screen height, just below the top-right clock/date/weather/icons (Zohn's pick "B", 2026-10-20). It is clear of long hero titles and sinks fully below the horizon before hiding. `sunCenterY()` in SunsetScene.kt is unit-tested.
  It sinks fully below the horizon at sunset and is hidden at night (stars instead).
- Horizon = the divider between hero and dock; one line.
- The perspective grid floor sits below the horizon and scrolls toward the viewer when idle (pauses on
  key press, resumes after 2 s); static at night. No app backdrop for Zohn.
- Palette slots: `accent` = purple (frames, date, lines), `glow` = orange (focus frame + glow, name),
  `text`, `surface` = ground at 85-90% alpha, `background` = ground. Night: no focus bloom, grid alpha 0.35.

| Phase | sky1 / sky2 / sky3 / horizon / ground | Sun (top/mid/bottom) | Accent 1 (orange) / Accent 2 (purple) / text |
|---|---|---|---|
| Morning "Sunrise" | #1C0F3A / #5A2C7A / #C9668A / #FFB27A / #120821 | #FFE0A3 / #FF9E5E / #E0567A, just cresting | #FF8A3D / #D8B4FE / #FFF1E6 |
| Day "High sun" | #2A0E55 / #6B2A8F / #B9417F / #FF7A3D / #14061F | #FFD07A / #FF7A3D / #D6336C, high | #FF8A3D / #D8B4FE / #FFF1E6 |
| Evening "Sunset" | #170730 / #4A1260 / #9C1F5E / #FF5E3A / #100418 | #FFB347 / #FF5E3A / #C2185B, half-set | #FF6B35 / #C4A1F5 / #FFE9DC |
| Night "Afterglow" | #05030D / #120826 / #24103D / #3A1A55 / #06030C | none; stars at 0.9 | #B26A3D / #8E7AB8 / #C9BFD6 |

## Moods
Phase boundaries are the midpoints of 45-minute blends, from KC (39.10, -94.58) sunrise/sunset:
Morning = sunrise - 30 min; Day = sunrise + 3 h; Evening = sunset - 1 h; Night = sunset + 2.5 h.
The effective palette is a per-channel blend of the current and next phase palettes. Night is low-glare
in both profiles (no focus bloom, lower contrast). Debug: hold Menu on the profile name for "fast day".

## Focus
Scale 1.1 + glow (stacked rounded rects drawn behind the tile) + 3 dp frame in the glow color. Unfocused
tiles dimmed to 72%. Must read clearly from the couch. No glow at night.

## Background (Zohn, back to front)
1. Scene: sky gradient, stars, striped sun, horizon, ground, soft veil behind the hero; rendered on the CPU
   into one 960x540 bitmap and stretched.
2. Grid floor: line-drawn per frame (below the horizon only) on its own graphics layer, scrolling slowly when idle. The root background fill sits under Zohn's opaque scene (harmless).

## Type
- Zohn: Orbitron display + JetBrains Mono data (above). Sydney: Playfair Display. Small labels: system sans.

## Performance (the design depends on these)
The TV has a Mali-G31 GPU and 1.8 GB RAM. Measured (real night): janky frames 6.7%, 50th percentile 17 ms, GPU 5 ms.
- No live `Modifier.blur` and no gradients shaded per frame. Static layers are drawn once into
  small bitmaps and stretched (Zohn's scene: 960x540).
- The grid is line-drawn, not a full-screen fill; the scene re-renders at most every 2-10 min (on mood ticks), never per frame.
- Every full-screen layer costs a full 1080p GPU fill per frame. Keep it to two (Zohn: scene + grid).
- No animated shadows (`Modifier.shadow` elevation). Use the drawBehind glow.
- Focus state is read only by the hero, so moving focus doesn't recompose the screen.
- Tile animations read state only inside `graphicsLayer` / `drawBehind` blocks.

## Sydney's profile (decided 2026-10-20)

**Theme:** Monet's Water Lilies. Light and airy by day, a dark "evening pond" at night; the painting is the star.

| Phase | background / surface / accent / glow / text | Painting subjects |
|---|---|---|
| Morning "Dawn mist" | #E6E3EE / #F3F0F2 / #7C9A8A / #9B86C8 / #2A3440 | sunrise, morning |
| Day | #DCE7EE / #EEF2EC / #6E8F7A / #8C7BC0 / #22313A | garden, water lilies, river |
| Evening "Sunset on the pond" | #F0DCCB / #F6E9DE / #9A7B5C / #B0679A / #3A2A2E | sunset, evening |
| Night "Evening pond" | #10202A / #1B3038 / #A99BD0 / #EBA9BE / #F4EFE6 | night, moonlight, nocturne |

Secondary (lily pink) stays #E6A3B4 except at night (#8FB59A sage).

- Display font: **Playfair Display** (OFL, `res/font/playfair*.ttf`).
- Wallpaper: one painting per phase from the Art Institute of Chicago's public-domain Impressionists,
  chosen by the phase's subjects (title match first). Fallback: any piece by the day's painter, any cached
  painting, then a pond placeholder. Each crossfades in at its phase midpoint; fetched lazily, cached.
- Blends: during a light/dark blend Sydney's text and wordmark pick the higher-contrast endpoint against the blended background (never a lerped mid-grey).
- Mist layer (pre-rendered) over the painting: top band, a stronger patch behind the clock/date/weather,
  a left fade behind the hero, and a bottom fade behind the dock. At night it becomes the dark veil
  (painting ~55% visible). No app backdrop and no drift.
- Art card under the name: italic title, ARTIST · YEAR in the accent color, then a 1-2 sentence fact (max 190 chars); follows the visible painting.
- Idle gallery (after ~9.5 min untouched on the home screen): paintings shown whole (Fit) on the pale
  background with a centered museum caption, cross-fading every 20 s, using the current phase's subjects.
  Any key exits; that key press is swallowed.

