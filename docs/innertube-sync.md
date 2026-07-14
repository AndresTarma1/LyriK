# Syncing `innertube/` with Metrolist (git subtree)

`innertube/` is tracked as a **git subtree** of the `innertube/` subdirectory from
[MetrolistGroup/Metrolist](https://github.com/MetrolistGroup/Metrolist) (an Android app; LyriK is
desktop-only, so this module is adapted — see "Known LyriK-specific diffs" below).

Set up once (already done): a `metrolist` remote points at their repo, and `git subtree split`
extracts just the `innertube/` subdirectory's history into a local branch, since `git subtree`
can't track a *subdirectory* of someone else's repo directly — it needs that history isolated
first.

## To pull a fresh sync

```bash
git checkout -b sync-innertube-<date>   # do this on a branch, not main
git fetch metrolist main
git subtree split --prefix=innertube metrolist/main -b metrolist-innertube-only
git subtree merge --prefix=innertube metrolist-innertube-only --squash
```

The merge will likely conflict wherever LyriK's own edits touch the same lines upstream changed —
resolve those normally (`git status`, edit, `git add`, `git commit`).

## After every sync, re-check these LyriK-specific diffs (upstream doesn't have them)

These aren't tracked by any tool — grep for them / diff manually after each pull, since a subtree
merge can silently overwrite them if upstream also touched the same file:

1. **`innertube/build.gradle.kts`** — LyriK uses `kotlin("jvm")` (plain JVM module); upstream uses
   `com.android.library`. Always restore LyriK's version wholesale after a sync, don't try to
   merge it.
2. **Logging: Napier, not Timber.** Upstream logs via `timber.log.Timber` (Android-only). LyriK
   has no Timber dependency — everything else in the app already uses
   `io.github.aakira.napier.Napier`. If upstream adds new `Timber.x(...)` calls, swap them to
   `Napier.x(...)` (same method names: d/i/w/e/v). **Careful**: `Timber.e(exception, "msg")` puts
   the exception first; `Napier.e("msg", exception)` puts it second — swap the argument order, not
   just the type name.
3. **`YouTube.addToPlaylist()` must keep parsing `setVideoId`** from the response
   (`AddItemYouTubePlaylistResponse.playlistEditResults.firstOrNull()
   ?.playlistEditVideoAddedResultData?.setVideoId`), returning `Result<String?>`. Upstream has
   simplified this to just return the raw `Result<HttpResponse>` at times — LyriK's two-way
   playlist sync (`PlaylistRepository`, `LibraryViewModel`) needs the parsed `setVideoId` to later
   call `removeFromPlaylist`.
4. **`HomePage.Section.numItemsPerColumn`** — LyriK-only field (`Int? = null`) powering the
   home-screen grid-shelf rendering, populated from
   `MusicCarouselShelfRenderer.numItemsPerColumn` in `fromMusicCarouselShelfRenderer`. Not present
   upstream; re-add if a sync drops it.
5. **`OfflineGate.kt` + the `install(OfflineGatePlugin)` line in `InnerTube.createClient()`** —
   LyriK's offline-mode kill switch. Every `YouTube.*` call goes through this one `HttpClient`, so
   this single plugin is what blocks all network traffic app-wide when offline mode is on
   (`com.example.melodist.utils.OfflineModeController` in `shared` flips the flag). Not present
   upstream — re-add the file and the `install()` line if a sync drops them.
6. **`PageHelper.extractArtists()` filters by `browseId`, not by separator text.** The Napier debug
   logging in this function is LyriK-only (upstream uses Timber, see #2). The filter itself must
   keep `runs.filter { it.navigationEndpoint?.browseEndpoint?.browseId != null }` — an earlier
   version filtered by `run.text != " • "`, which only strips that one literal separator and lets
   everything else through unfiltered (" y ", " & ", commas, and the trailing view-count run like
   "1527 M de vistas") — those rendered as bogus extra "artists" in song subtitles. Only runs with
   a real artist browseId are genuine artists; re-apply the browseId filter if a sync reverts it.
7. **`Runs.kt`'s `List<Run>.oddElements()` filters by browseId link, not `index % 2 == 0`.** Used
   in 18+ artist/album parsing call sites (`NextPage.kt` radio/mix queue, `HomePage.kt`,
   `SearchPage.kt`, etc.) — same root cause and symptom as #6, just a different, much more widely
   used function. The old positional assumption (artists and separators strictly alternate) broke
   with 3+ artists or mixed ", "/" y " separators, producing blank/duplicated names and leaking the
   trailing view-count run into radio/mix queue subtitles. Falls back to the old `index % 2 == 0`
   *only* when zero runs in the list have a link at all (label-uploaded albums/tracks name the
   artist as plain unlinked text — see the comment in `AlbumPage.kt`'s `.ifEmpty {}` fallback around
   line 104). Two call sites (`YouTube.kt` playlist parsing, `HomePage.kt` album parsing) had a
   `.drop(1)` after `.oddElements()` that assumed index 0 was always a leading type-label ("Album")
   picked up by the old positional logic — removed both, since the browseId filter already excludes
   the (unlinked) label; re-check those two spots don't need `.drop(1)` re-added if a sync reverts
   `oddElements()`.

After resolving conflicts and reapplying the above, run `./gradlew :composeApp:compileKotlinJvm`
— any other upstream API signature changes will surface as compile errors in `shared`/`composeApp`
(that's normal; fix the call sites or the innertube function as appropriate).

## Cleanup after a sync

The `metrolist-innertube-only` split branch is disposable — delete it once merged
(`git branch -D metrolist-innertube-only`); regenerate it fresh next time. Keep the `metrolist`
remote for future syncs.
