# Changelog — v0.6.0

All notable changes from v0.5.0 to v0.6.0.

---

## Features

### Fullscreen Now Playing
- New **Fullscreen player** toggle in Player settings (index 5, count 6)
- When enabled + Now Playing expanded: hides NavigationRail and bottom MiniPlayer for an immersive view
- Floating transparent MiniPlayer overlay at the bottom, appears on mouse movement and auto-hides after 3 seconds
- Content padding removed in fullscreen mode for edge-to-edge experience
- DataStore key `full_screen_player` with flow in `UserPreferencesRepository` + `SettingsViewModel`

### Crash Reporting via GitHub Issues
- `PlatformCrashHandler` saves JSON crash reports to `%LOCALAPPDATA%\Tarma\LyriK\logs\crash\`
- On next startup, `CrashReportDialog` shows unsent reports with details (version, OS, timestamp)
- Sends reports as pre-filled GitHub Issues via `Desktop.browse()`
- `CrashReportRepository` in shared module: write/read/send crash reports
- Ring buffer for recent logs (50 entries) in crash handler
- `Runtime.getRuntime().halt(1)` ensures app actually closes after saving crash report
- **Send crash report** action in Support settings section with badge showing pending count
- 14 crash-related i18n strings (ES/EN)

### Navigation Rail Styles
- Two styles: **DEFAULT** (compact icons) and **WIDE** (icons + labels)
- Toggle in Appearance settings
- Style persisted in DataStore

---

## Refactors

### Package Rename
- `melodist` → `musicApp` across all Kotlin sources, SQLDelight `.sq` files, and `build.gradle.kts`

### Settings Split into Folder-Based Sections
- `SettingsScreen.kt` split into `settings/` folder with individual section files:
  - `AppearanceSection.kt`, `AudioSection.kt`, `PlayerSection.kt`, `SyncSection.kt`, `SystemSection.kt`, `OverlaySection.kt`, `SupportSection.kt`
- All sections use M3 Expressive `ListItemDefaults.segmentedShapes(index = N, count = M)` with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`

### NowPlayingLayouts Split
- `NowPlayingLayouts.kt` split into 7 files:
  - `QueueItem.kt`, `PlaybackQueuePanel.kt`, `SongInfoTab.kt`, `LyricsTab.kt`, `NowPlayingOverlay.kt`, `CoverArt.kt` + trimmed orchestrator

### Shared Item Composables
- New `shared/items.kt` with `onYTItemClick`, grid items, section items for reuse across screens

### Context Menu Refactor
- `SongContextMenuHandler.kt` refactored for cleaner structure

---

## Fixes

### Dropdown Menus
- Settings dropdown menus now close on item selection (was missing `onExpandedChange(false)` in `DropdownMenuItem.onClick`)

### i18n
- Replaced hardcoded Spanish strings with `stringResource()` in:
  - `SongInfoTab.kt` (4 strings)
  - `ArtistModal.kt` (1 string)
  - `playlist/items.kt` (1 string)
  - `HomeScreen.kt` (1 string)
- Added 15 missing EN translations (Spotify import + navigation rail)
- Fixed CrashReportDialog format strings (`.replace()` workaround for Compose Multiplatform `%d`/`%s` bug)

### Player
- Disabled poToken for stream resolution (JCEF consumed too much RAM and caused UI stuttering)
- Volume persistence: added `readSavedVolume()` suspend fun, changed `PlayerService.loadSavedVolume()` to use `runBlocking`
- Volume slider performance: debounced persist (500ms), extracted `_volume` as separate `MutableStateFlow`, removed `volume` from `PlayerUiState`

---

## Dependencies
- Updated various dependencies and build config

---

## Known Issues (carried forward)
| P | Issue |
|---|-------|
| 0 | `$$` string templates may fail on non-K2 |
| 0 | `runBlocking` on UI thread in `App.kt` |
| 0 | Volume unit mismatch (x100 vs /100) |
| 1 | DB deleted on schema version change |
| 1 | `catch(Throwable)` swallows CancellationException |
| 1 | `INSERT OR REPLACE` loses play counts/liked dates |
| 1 | No `PRAGMA foreign_keys = ON` |
| 2 | ViewModels are singletons, not component-scoped |
| 2 | Position ticker polls 1000ms instead of MPV events |
| 2 | `Route` and `ScreenConfig` duplicated sealed classes |
| 3 | Crossfade preference exists but unused |
| 3 | No localization (all Spanish hardcoded in some areas) |
| 3 | Version string duplicated (build.gradle ↔ AppViewModel) |
