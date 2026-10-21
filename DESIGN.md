# Zohn TV — Design (decided 2026-10-20)

Two profiles share one layout: **Zohn** (baroque-futurist, below) and **Sydney** (Monet's Water
Lilies, see the end). Colors come from `LocalPalette` — never hard-code a profile's colors in UI code.

**Theme:** baroque-futurist — gold on black. Cathedral ornament meets spaceship light.
Any new visual feature: ask Zohn about style, colors and effects **before** building it.

## Layout: Hero + dock
- Top bar, left: `ZOHN` wordmark.
- Top bar, right: clock, date, weather line (°F), then a row of round gold quick-action icons:
  Inputs (only if the TV exposes HDMI inputs), Wi-Fi, Bluetooth, Settings. Reached with Up from the dock.
- Hero: focused app's name, large (or the focused quick action's name), with a hint line.
- Ornament divider: thin gold rule with a center diamond.
- Dock: one horizontal row of app banners, **most-used first** (launches from Zohn TV), then A–Z.
  Settings lives in the quick actions, not the dock.
- Hold OK (or press Menu) on a tile: menu with Hide / Unhide, App info, Show hidden apps.
  Hidden apps, when shown, are dimmed.
- Menus: dark walnut panel, thin gold border, Cinzel title, gold-highlighted focused row.

## Palette
| Token | Hex | Use |
|---|---|---|
| Ink | `#0B0806` | background |
| Walnut | `#1A120C` | tile and panel surface |
| Gold | `#D4AF37` | ornament, focus frame + glow, icons |
| Oxblood | `#7A1E2C` | secondary accent, gradient tint |
| Ivory | `#F3E9D2` | text |

## Focus
Scale 1.1 + gold glow (stacked rounded rects drawn behind the tile) + 3 dp gold frame. Unfocused
tiles dimmed to 72%. Must read clearly from the couch.

## Background (back → front)
1. Wallpaper (Zohn supplies; gradient placeholder until then), focused app's banner blurred at
   22%, and a darkening scrim, **flattened into one small bitmap per focused app**; crossfades on
   focus change.
2. Slow (30 s) gold/oxblood drift layer, oversized ×1.5 so its edges never show.

## Type
- Display (clock, hero title, wordmark, menu titles): **Cinzel** (OFL, `res/font/cinzel.ttf`).
- Small labels: system sans.

## Performance (the design depends on these)
The TV has a Mali-G31 GPU and 1.8 GB RAM. Measured: navigation ~24 ms/frame, GPU ~8 ms.
- No live `Modifier.blur` and no gradients shaded per frame. Static layers are drawn once into
  320×180 bitmaps and stretched; blur is faked by stretching a 32×18 copy of the banner.
- Every full-screen layer costs a full 1080p GPU fill per frame. Keep it to two.
- No animated shadows (`Modifier.shadow` elevation). Use the drawBehind glow.
- Focus state is read only by the hero and backdrop, so moving focus doesn't recompose the screen.
- Tile animations read state only inside `graphicsLayer` / `drawBehind` blocks.
- A real wallpaper photo will need its own full-resolution layer (3 layers total). Budget for it.

## Sydney's profile (decided 2026-10-20)

**Theme:** Monet's Water Lilies, daylight pond. Light and airy; the painting is the star.

| Token | Hex | Use |
|---|---|---|
| background | `#DCE7EE` | pale water/sky; also the mist over the painting |
| surface | `#EEF2EC` | mist: tiles, art card, menus |
| accent | `#6E8F7A` | sage: name, date, ornament, frames |
| glow | `#8C7BC0` | violet: focus frame + glow |
| secondary | `#E6A3B4` | lily pink |
| text | `#22313A` | deep blue-grey |

- Display font: **Playfair Display** (OFL, `res/font/playfair*.ttf`).
- Wallpaper: today's painting, full-screen (Crop), from the Art Institute of Chicago's public-domain
  Impressionists, weighted toward Monet. Same piece all day; cached for offline.
- Mist layer (pre-rendered) over the painting: top band, a stronger patch behind the clock/date/weather,
  a left fade behind the hero, and a bottom fade behind the dock. No app backdrop and no drift.
- Art card under the name: italic title, ARTIST · YEAR in sage, then a 1–2 sentence fact (≤190 chars).
- Idle gallery (after ~9.5 min untouched on the home screen): paintings shown whole (Fit) on the pale
  background with a centered museum caption, cross-fading every 20 s. Any key exits; that key press
  is swallowed.
