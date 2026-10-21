# Zohn TV — custom Google TV launcher

A personal home-screen launcher (`cc.zohn.tvlauncher`) for one specific TV, sideloaded via
Developer Mode. Kotlin + Jetpack Compose (Compose for TV). Moods through the day: each profile's look follows Kansas City's sunrise and sunset.

**Read before changing anything:** [RULES.md](RULES.md) (hard rules + amendments) and
[DESIGN.md](DESIGN.md) (look, layout, and the performance rules the design depends on).

## Status (2026-10-20)

Working on the TV:
- Lists every TV app (`LEANBACK_LAUNCHER` query), launches it, Back returns to the launcher.
- **Is the Home screen**: boots into Zohn TV, and the remote's Home button lands here (see "Home button" below).
- Hero + dock layout, focus glow, clock/date/weather, per-profile look (see DESIGN.md).
- Dock sorted by most-used (launches from Zohn TV only), app list cached on disk for ~1 s cold start.
- Hold OK (or Menu) on a tile → Hide / App info / Show hidden apps.
- Quick-action row under the clock (press Up from the dock): Inputs (HDMI 1–3, verified switching), Wi-Fi, Bluetooth, Settings.
- **Two profiles, Zohn and Sydney** (inside the launcher, not Google TV profiles). Switch by focusing
  the name top-left (Up, then Left) and pressing OK. Each has its own look, most-used order and hidden
  apps; the TV opens in whoever used it last. Sydney's is art-focused: today's Impressionist painting
  as wallpaper, a museum-label card with a fact, and an idle gallery wall. See DESIGN.md.
- Weather line for Kansas City, MO (°F, Open-Meteo, no key; `WEATHER_CITY` in `Weather.kt`).
- **Moods:** four phases (morning, day, evening, night) timed by Kansas City's sunrise and sunset, with
  45-minute blends between them. Night is a low-glare dark mode for both profiles.
- **Zohn's Sunset grid:** purple + orange synthwave sky, striped sun that tracks the real sun, horizon on
  the divider, perspective grid that scrolls while idle (static at night).
- **Sydney's phase paintings:** a different Impressionist painting per phase, chosen by subject
  (dawn, garden, sunset, night); night dims the painting and lightens the text.
- **Greeting on wake:** "Good morning, {name}" etc. for ~4 s when the launcher shows right after the TV
  slept 30+ minutes, and on profile switch. Any key skips it.
- **Usage log:** every dock launch is appended to `files/usage.log` (time, weekday, hour, phase, profile,
  package; 90 days kept). Nothing reads it yet; it's for the planned Habits feature.
- **Fast-day debug:** hold Menu on the profile name to play a whole day in ~2 minutes (toast "Fast day on/off").
- The grid scroll pauses while the remote is in use and resumes after 2 s idle.

Not done / open:
- **Wallpaper** image: Zohn will supply one; a gradient placeholder is drawn in `Background.kt`.
- The idle gallery hasn't been watched end-to-end on the TV yet (it needs ~9.5 min of no input).
- Navigation runs ~24 ms/frame (GPU ~8 ms); the rest is CPU. See DESIGN.md → Performance.

## Code map (`app/src/main/java/cc/zohn/tvlauncher/`)

| File | What it does |
|---|---|
| `MainActivity.kt` | State (apps, hidden, inputs), refresh-from-cache logic, launching, sorting by use |
| `LauncherScreen.kt` | Screen composition: background, top bar, hero, divider, dock, dialogs |
| `Background.kt` | Background layers: Zohn's scene + grid, Sydney's painting + mist (see DESIGN.md Performance) |
| `SunCalc.kt` | NOAA solar math: sunrise, sunset, sun elevation (pure Kotlin) |
| `Mood.kt` | Phase boundaries from sun times, blend windows, `MoodState` |
| `MoodClock.kt` | Ticking mood state (phase, blend, sun elevation), fast-day debug |
| `Moods.kt` | Phase palettes per profile and `blend()` |
| `SunsetScene.kt` | Renders Zohn's sky/sun/horizon scene and grid floor bitmaps |
| `Greeting.kt` | Wake detection and greeting text |
| `UsageLog.kt` | Launch history file for the Habits feature |
| `TopBar.kt` | Wordmark, clock, weather line, quick-action buttons |
| `Dock.kt` | App row; tile focus visuals; OK vs. hold-OK/Menu key handling |
| `Menus.kt` | App menu and input picker dialogs |
| `AppRepository.kt` | App discovery (`LEANBACK_LAUNCHER`), launch intents, on-disk app cache |
| `LauncherPrefs.kt` | Active profile; per-profile hidden apps + launch counts; package-change bookkeeping |
| `TvInputs.kt` | HDMI inputs via `TvInputManager`, passthrough intents |
| `Weather.kt` | Open-Meteo fetch + `WEATHER_CITY` setting |
| `HomeRedirectService.kt` | Accessibility service that sends the Home button here (see below) |
| `Profiles.kt` | The profiles (Zohn, Sydney): id, name, palette, art focus |
| `Art.kt` | Art Institute of Chicago fetch: daily painting, gallery set, image cache, facts |
| `Gallery.kt` | Sydney's idle gallery wall |
| `Http.kt` | Shared HTTP helpers (JSON GET, file download) |
| `ui/theme/Palette.kt` | `LauncherPalette` fields + fonts (`LocalPalette`); phase palettes live in `Moods.kt` |

## Tests

`./gradlew.bat testDebugUnitTest` (JVM unit tests: SunCalc, Mood, Moods, art subjects, greeting, usage log).

## Build & deploy

The TV is on wireless debugging. It can show up twice in `adb devices`, so always pass `-s`.

**Normally just run `scripts/deploy.sh`** (build release → install → AOT compile → launch). It picks
the single connected device, or pass a serial. Manual steps, for reference:

```sh
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"   # Git Bash on Windows
export MSYS_NO_PATHCONV=1                                        # stop Git Bash rewriting /data/... paths
./gradlew.bat assembleRelease
adb -s <serial> install -r app/build/outputs/apk/release/app-release.apk
adb -s <serial> shell cmd package compile -m speed -f cc.zohn.tvlauncher   # see note below
```

- Use the **release** build. It's signed with the debug key (sideload only) so it installs over
  debug builds; debug Compose is unusably slow on this TV.
- **Always AOT-compile after installing.** A fresh install runs interpreted and is 3–5× slower
  until Android compiles it in the background (which can take a day).
- Serial: changes between sessions (`adb-C87EA1FEC04E-…._adb-tls-connect._tcp` or `172.20.93.10:5555`); `adb devices` / `adb mdns services` show it.
- `app/src/main/baseline-prof.txt` marks all launcher code for ahead-of-time compilation when Android next optimizes the app.

## The TV (target hardware)

Philips (TP Vision/"MOKA") **R8G**, Google TV, Android 14 / API 34. Realtek rtd2851f SoC,
**ARM Mali-G31** GPU (entry-level), **1.8 GB RAM**. UI renders at 1920×1080 on a 4K panel.
Stock launcher: `com.google.android.apps.tv.launcherx`.

## Hard-won lessons

- **adbd eats the TV's RAM.** After many screenshots/logcat/installs over wireless adb (plus
  Android Studio's logcat), the device-side `adbd` grew to 470 MB → swap full → the whole TV crawled
  and the launcher was killed every ~15 s. Fix: `adb -s <serial> tcpip 5555` restarts adbd. Keep
  adb traffic light; check `top | grep adbd` in long sessions; close Android Studio's Logcat.
- **Screenshots:** `adb exec-out screencap` gets truncated over wireless. Use
  `shell screencap -p /data/local/tmp/s.png` then `pull`.
- **R8:** the Android Studio template's `optimization { packageScope = ... }` produced an
  `IllegalAccessError` crash at startup. It's removed; R8 now optimizes the whole app.
- **Measuring frames:** `dumpsys gfxinfo cc.zohn.tvlauncher reset`, navigate, then read the
  percentiles. Measure after AOT compile and after the TV settles; numbers right after an install
  are polluted.

## Screensaver vs. Sydney's gallery

The TV's own screensaver starts after `screen_off_timeout` (10 min). The gallery starts 30 s before
that, then holds the screen awake (`FLAG_KEEP_SCREEN_ON`) only until the TV's `sleep_timeout`
(20 min total) would have turned it off, so the TV still sleeps on schedule. See `MainActivity`.

## Home button

Google TV sends the Home key straight to the stock launcher even when another app holds the
HOME role. Disabling the stock launcher is forbidden (RULES.md), so `HomeRedirectService` (an
accessibility service) watches for the stock launcher's **home activity only** and brings Zohn TV
forward. Its other screens (quick settings panel, input picker) are left alone. It must be switched
on by hand: Settings → Accessibility → **Zohn TV Home button** (done on 2026-10-20). The HOME role
was also set with `cmd package set-home-activity`, which makes the TV boot into Zohn TV.

## Reverting to stock (one step, always)

- `adb uninstall cc.zohn.tvlauncher` — removes everything, stock behavior returns, or
- turn off **Zohn TV Home button** in Accessibility (stock Home button returns; Zohn TV stays installed).
