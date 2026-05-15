# Melodist Engineering Rules

## Architecture
- Follow Clean Architecture boundaries: UI renders state and sends user intents; ViewModels coordinate use cases; repositories/services own data, network, database, player, and download work.
- UI composables must not call backend/network/database APIs directly. If a screen needs new data, expose it through a ViewModel state flow.
- Keep domain models and UI models explicit. Do not leak SQLDelight rows or transport DTO details into composables unless a mapper already makes that the local convention.
- Prefer existing repositories, mappers, CompositionLocals, and player/download helpers before adding new abstractions.
- Put cross-screen behavior in shared components or ViewModels, not copied into each screen.

## State And Side Effects
- State flows should be the source of truth for screen data. Avoid duplicated mutable UI state unless it is purely ephemeral, such as hover, dialog visibility, selected row ids, or text-field drafts.
- Compose side effects (`LaunchedEffect`, `DisposableEffect`) should be small and UI-scoped. Network calls, DB writes, playback actions, and downloads must go through ViewModels/services.
- Keep selection, dialogs, and context menus predictable: clear transient selection after destructive or bulk actions.
- Do not block initial composition with image processing, palette extraction, large list transforms, or synchronous file/network work.

## Performance
- Cache expensive derived data such as artwork palettes and metadata conversions.
- Use stable lazy-list keys based on ids. Avoid index keys unless the item has no stable id.
- Avoid full-screen recomposition for frequent player progress updates. Subscribe narrowly to progress only where progress is drawn.
- Do not upscale artwork or extract dynamic colors in first-render paths unless the user explicitly needs high-resolution art.
- Prefer `remember`, `derivedStateOf`, and precomputed ViewModel state for expensive list filters/grouping.

## UI Quality
- Screens should feel like production music software: dense, calm, responsive, and scan-friendly.
- Avoid decorative cards inside cards. Use surfaces for major panels and cards for repeated media items or dialogs.
- Every visible control should do something useful or be omitted until implemented.
- Buttons with icons need clear content descriptions. Use familiar Material icons when available.
- Loading states should match the target screen layout closely.

## Data And Downloads
- Local playlists must react to song additions/removals: update count, thumbnail, and displayed list consistently.
- Download state must flow from `DownloadViewModel`; UI should not infer download completion from files directly.
- Bulk actions should reuse single-item code paths where possible, so playlist count, thumbnails, and metadata resolution stay consistent.

## Git And Maintenance
- Keep edits scoped to the requested behavior.
- Never revert unrelated local changes.
- Run `compileKotlinJvm` after Kotlin/SQLDelight changes when possible.

---

# Session Context (v0.1.1)

## Project Structure
- **composeApp/** — Compose Multiplatform Desktop (JVM), Koin DI, decompose navigation
- **shared/** — Common business logic, SQLDelight DB, repositories, ViewModels
- **innertube/** — YouTube Music API wrapper (NewPipe + custom parsing)
- **Audio engine** — mpv via `MpvLib.kt` (WASAPI, Windows-only)
- **Data paths** — `%LOCALAPPDATA%\Melodist\` (DB, DataStore, cache, downloads)

## Key Files
- `composeApp/src/jvmMain/kotlin/com/example/melodist/navigation/Navigation.kt` — Desktop layout, keyboard shortcuts, queue/now-playing panels
- `composeApp/src/jvmMain/kotlin/com/example/melodist/ui/components/MiniPlayer.kt` — Bottom bar with tooltips, like button, volume badge
- `composeApp/src/jvmMain/kotlin/com/example/melodist/ui/components/player/NowPlayingLayouts.kt` — Queue panel with sticky now-playing header, QueueItem
- `composeApp/src/jvmMain/kotlin/com/example/melodist/ui/components/EqualizerPanel.kt` — Shared 10-band EQ component
- `composeApp/src/jvmMain/kotlin/com/example/melodist/ui/screens/SettingsScreen.kt` — Settings with responsive dialogs, JVM config entry
- `composeApp/src/jvmMain/kotlin/com/example/melodist/ui/screens/AdvancedJvmSettingsScreen.kt` — JVM config UI (Xmx, Xms, G1GC, ZGC, restart)
- `composeApp/src/jvmMain/kotlin/com/example/melodist/ui/screens/library/LibraryScreen.kt` — Library with search, sort, YTM filter dropdown
- `shared/src/commonMain/kotlin/com/example/melodist/viewmodels/LibraryViewModel.kt` — Combined local+YTM flows, filtered/sorted
- `shared/src/commonMain/kotlin/com/example/melodist/viewmodels/HomeViewModel.kt` — Home page + recent songs from DB
- `shared/src/jvmMain/kotlin/com/example/melodist/viewmodels/PlayerViewModel.kt` — Player state, queue, playback, like, volume, mute
- `shared/src/jvmMain/kotlin/com/example/melodist/player/PlayerService.kt` — mpv wrapper, volume, EQ, position ticker
- `shared/src/jvmMain/kotlin/com/example/melodist/player/MpvAudioPlayer.kt` — Low-level mpv JNI bindings
- `shared/src/jvmMain/kotlin/com/example/melodist/data/local/DatabaseDriverFactory.kt` — SQLite driver, schema version check (DESTRUCTIVE)
- `shared/src/commonMain/kotlin/com/example/melodist/data/repository/UserPreferencesRepository.kt` — DataStore preferences
- `shared/src/commonMain/kotlin/com/example/melodist/data/repository/JvmConfigRepository.kt` — JVM config persistence
- `shared/src/jvmMain/kotlin/com/example/melodist/utils/AppRestarter.kt` — ProcessBuilder restart with JVM args
- `shared/src/commonMain/kotlin/com/example/melodist/db/DatabaseDao.kt` — Unified DB access, artist/album mappings

## Completed
- Library search filters local + YTM combined (not just local)
- YTM library filters via innertube `LibraryFilter` (recent activity, playlists)
- YTM filter as icon + dropdown menu next to library tabs
- Album/Playlist screens use solid `surface` background (no blurred artwork)
- Equalizer sliders increased to 180dp height
- Queue now-playing item uses `stickyHeader` with progress bar, primary border, badge
- Global keyboard shortcuts: Space (play/pause), M (mute)
- Improved empty states for queue and library sections
- Fixed quick picks missing artist metadata (save artist maps on toggleLike/cache)
- Settings dialogs responsive with `BoxWithConstraints`
- Advanced JVM configuration screen with restart
- Version bumped to 0.1.1 with tag

## Known Issues (Prioritized)
| P | Issue | Location |
|---|-------|----------|
| 0 | `$$` string templates may fail on non-K2 | `DownloadRepository.kt:198,519,542` |
| 0 | `runBlocking` on UI thread | `App.kt:217` |
| 0 | Volume unit mismatch (x100 vs /100) | `MpvAudioPlayer.kt:80` vs `PlayerService.kt:103` |
| 1 | DB deleted on schema version change | `DatabaseDriverFactory.kt:40-43` |
| 1 | `catch(Throwable)` swallows CancellationException | `PlayerService.kt:166` |
| 1 | `INSERT OR REPLACE` loses play counts/liked dates | `Song.sq:71` |
| 1 | No `PRAGMA foreign_keys = ON` | `DatabaseDriverFactory.kt` |
| 2 | ViewModels are singletons, not component-scoped | `AppModule.kt` |
| 2 | Position ticker polls 250ms instead of MPV events | `PlayerService.kt:170` |
| 2 | `Route` and `ScreenConfig` duplicated sealed classes | `Navigation.kt`, `Route.kt` |
| 3 | Crossfade preference exists but unused | `UserPreferencesRepository.kt:72` |
| 3 | No localization (all Spanish hardcoded) | Throughout |
| 3 | Version string duplicated (build.gradle ↔ AppViewModel) | `build.gradle.kts:110`, `AppViewModel.kt:29` |

## Build Commands
- Compile: `.\gradlew compileKotlinJvm --quiet`
- Run: `.\gradlew :composeApp:run`
- Package MSI: `.\gradlew :composeApp:packageDistributionForCurrentOS`
