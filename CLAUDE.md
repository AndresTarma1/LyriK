# Melodist — Development Guidelines

## Architecture: Component Principles

### No Hidden Dependencies (`koinInject()` prohibited in components)

Reusable UI components **must not** call `koinInject()` internally. This creates hidden
coupling (Hidden Dependency) and breaks the principle of stateless/reusable components.

**Rule**: ViewModels and repositories that a component needs must be passed as parameters
by the parent. The component only receives data and emits events — it never resolves its
own dependencies from the DI container.

**Acceptable** at screen level (`*Screen.kt`): screens are top-level entry points and may
use `koinInject()` or `CompositionLocal` to obtain ViewModels.

```kotlin
// BAD — hidden dependency inside a reusable component
@Composable
fun SongContextMenuContent(...) {
    val playlistsViewModel: LibraryPlaylistsViewModel = koinInject() // ✗
}

// GOOD — dependency passed from parent
@Composable
fun SongContextMenuContent(
    ...,
    onAddToPlaylist: () -> Unit, // parent handles the playlist logic
)
```

### Popup Ownership

`Popup` (and `DropdownMenu`) wrappers belong to the **parent** component, not to the
child content. Context-menu components render only their `Surface` + items and emit
actions; the parent decides when/how to show the popup.

```kotlin
// GOOD — parent owns the Popup
if (showMenu) {
    Popup(onDismissRequest = { showMenu = false }, ...) {
        SongContextMenuContent(song = song, onAction = { ... })
    }
}
```

### CompositionLocals

`LocalPlayerViewModel`, `LocalDownloadViewModel`, etc. are fine inside components — they
are the Compose-native way of providing ambient dependencies and are explicitly provided
at the app root.

## Theming

- **AMOLED (BLACK)**: all menus, dropdowns, and surfaces must use `surfaceContainer`
  with `tonalElevation = 0.dp` so the tonal overlay doesn't lighten them.
- **DIM**: accent-tinted dark gray surfaces.
- **DropdownMenu**: always pass `containerColor = MaterialTheme.colorScheme.surfaceContainer`
  and `tonalElevation = 0.dp` for AMOLED compatibility.

## Language

- UI strings are currently hardcoded in Spanish. Use `stringResource(Res.string.*)` where
  available; for new strings without a resource, keep them in Spanish for consistency.
