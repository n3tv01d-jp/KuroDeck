# KURODECK // 黒甲板

An Android home-screen launcher built from **GrapheneOS Launcher3** (branch `17`), dressed in the
sci-fi HUD language of [Hearth](../hearth): chamfered neon panels, Orbitron / Rajdhani /
Share Tech Mono type, Japanese subtitles, a hex-lattice backdrop, a boot sequence, nine palettes and
KAGARI, the kaomoji navigator.

It builds with Gradle as a normal, sideloadable APK (no AOSP tree needed) and installs next to the
stock launcher as `app.kurodeck.launcher`.

| Piece | What it does |
|---|---|
| **01 CORE 炉心** widget | Clock and date, a reactor ring (outer: battery, inner: memory), and a readout grid: memory, storage, network ↓/↑, battery temp/voltage, thermal status, link type, uptime |
| **02 NETWORK 通信** widget | Live RX/TX rates, totals since boot, and a 120 s throughput chart (in the widget picker) |
| **07 NAVIGATOR 案内役** widget | KAGARI comments on low or critical battery, the charger, thermal throttling, memory or storage pressure, lost signal, big downloads, app installs and unlocks. Otherwise she rotates idle lines every 14 s. Tap to skip to the next line |
| **COMMAND DECK 司令甲板** | Long-press the home screen → *Command deck*. Choose one of 9 palettes (HEARTH, REDLINE, MECHA, SAKURA, ECLIPSE, KURENAI, ABYSS, AURUM, SPECTER) and toggle the boot sequence, KAGARI's idle chatter, the hex backdrop, the paged drawer and the HUD panel |
| Boot sequence | Hearth's start-up log and progress bar, once per launcher process. Tap to skip. It is skipped when system animations are off |
| **03 APPS アプリ** drawer | The app drawer is paged: swipe left and right through fixed grids of apps in A–Z order, like One UI. Each page is a HUD panel with a header (`P01/05 · 087`), and a segmented indicator sits underneath. Search, the work tab and dragging apps out work as before. Toggle *Paged drawer* in the Command deck to go back to the vertical list |
| **06 CONSOLE 操作盤** panel | Swipe down on the home screen for KuroDeck's own pull-down: clock and status, 12 quick tiles, a brightness slider and your notifications (tap to open, swipe sideways to dismiss, CLEAR ALL). Swipes that start on the status bar still open the system shade. Android only lets apps toggle the torch, Do Not Disturb, rotation and brightness directly; Wi-Fi, internet, volume and NFC open the system's small settings panels, and the rest open Settings. Needs *Notification access* (and optionally *Modify system settings* and *Do Not Disturb access*), each asked for the first time it's needed. Toggle *HUD panel* in the Command deck |
| **04 FLEET 艦隊** widget | Your [HEARTH](../hearth) fleet on the home screen: one row per machine with its link light, CPU, memory, hottest sensor and containers, and the newest hub warning. Tap to open the HEARTH dashboard. Needs a linked hub |
| **HEARTH LINK 炉心連結** | Command deck → *HEARTH LINK*: enter your hub's address (`http://192.168.1.10:8088`). KuroDeck then follows the hub's fleet theme and palettes (picking one here changes it fleet-wide; toggle *Fleet theme* to opt out), KAGARI uses the hub's shared lines and reports its alerts (silent machines, critical events, warnings, recoveries), and the CONSOLE gets a **FLEET** section with the latest alerts and HEARTH / GATE / RYUCORE tiles. The hub is only polled while one of these is on screen. Unlink to go back to fully offline |
| HUD popups | The home-screen long-press menu (with an `[00] OPTIONS 操作` header) and app shortcut popups use the widgets' chamfered panels, flat rows with a glowing press bar, and Rajdhani caps |
| Launcher theming | Each palette is a full Launcher3 activity theme covering folders, popups, all-apps, page dots and drop targets. Material tokens are always dark. Icons default to a **hexagon** shape, and labels use Rajdhani |

CORE and NAVIGATOR are placed on the first screen of a fresh layout. All four panels are
ordinary resizable widgets under **KuroDeck** in the widget picker.

## Build

Requirements: a JDK 17+ with `javac` (21 recommended) and an Android SDK with platform
`android-37.2` (platform `android-37.0` is used by the libraries).

```sh
export JAVA_HOME=/path/to/jdk-21
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # signed with the debug key; use your own for real installs
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then choose **KuroDeck** as the home app: *Settings → Apps → Default apps → Home app*.

## Layout of the repo

```
launcher3/        GrapheneOS platform_packages_apps_Launcher3 (tests/quickstep/go removed), lightly patched
third_party/      frameworks/libs/systemui libs (iconloader, animation, msdl, usertype, dynamiccolors),
                  SystemUI plugin_core, font licences (OFL)
kurodeck/         KuroDeck's own code (com.kurodeck.*) and resources (override Launcher3's)
flags/            generated stand-ins for aconfig Flags classes      (tools/gen_flags.py)
stubs/            tiny stand-ins for platform-only SystemUI/WM-Shell/AppFunctions types
libs/*/           Gradle modules that point at the vendored sources
app/              the application module and merged manifest
tools/            gen_flags.py, import_flag_values.py, gen_resources.py
```

### Porting notes (what differs from the platform build)

* **Non-Quickstep variant.** Like every third-party launcher, KuroDeck can't host recents or the
  gesture navigation animations. Those stay with the system launcher (Quickstep).
* **aconfig flags** become plain Java classes whose values match what Android 17 ships with.
  `tools/import_flag_values.py` resolves GrapheneOS `build/release` (ap2a → … → cp2a) into
  `flags/flag_values.properties`, and `tools/gen_flags.py` turns that file into the classes. This
  matters because some of Launcher3's flag-off code paths no longer work. Framework flag packages (`android.os`, `com.android.window.flags`,
  …) are relocated under `kurodeck.flags.*`, because the device's boot classpath would otherwise
  shadow them with hidden-API versions. Rerun `tools/gen_flags.py` after updating Launcher3.
* **Hidden / GrapheneOS-only APIs.** `GosPackageState`, Storage Scopes, Contact Scopes and
  `startActivityAsUser` are called by reflection (`com.kurodeck.compat.PlatformCompat`). If the OS
  refuses a call, the dependent shortcut hides itself. `ActivityThread` (test detection) and
  `TextView#viewClicked` were replaced with public equivalents.
* **Dropped:** statsd logging (constants only), bubbles (always off), viewcapture, and the
  experimental AppFunctions "workspace functions" (behind the disabled `condo_planner` flag; it
  needs an unreleased androidx snapshot, so it is compiled against inert shims).
* **Resources:** `res-stock` is a lower-priority library (`:libs:stockres`), matching Soong's
  ordering, and KuroDeck's resources sit in the build-type source sets so they override Launcher3's.
* **Always dark, hex everywhere.** `Utilities.isDarkTheme` always returns true, the drawer sheet
  uses the palette colour, and the icon shape also applies outside the workspace (drawer, search).
* Every edit to vendored Launcher3 code is marked with a `KuroDeck:` comment
  (`grep -rn "KuroDeck:" launcher3`).

## Licences

Launcher3, the systemui libraries and plugin_core are © The Android Open Source Project /
GrapheneOS, under Apache 2.0. Orbitron, Rajdhani and Share Tech Mono are under the SIL Open Font
License (see `third_party/fonts`). KuroDeck's own code is Apache 2.0.
