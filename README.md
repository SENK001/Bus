# Bus Schedule（班车查询）

An Android app for viewing and managing bus/shuttle routes and their departure times.
The UI is localized in Chinese (Simplified).

- Package: `com.senk.bus`
- Single-module Gradle project (`:app`)
- Current app version: `1.0.0` (versionCode `100000`)

## Features

- **Route management** — create, edit and delete routes (name, origin, destination).
  The "add" action lives in the toolbar menu; there is no FAB.
- **Schedule management** — add departure times for a route with a custom hour/minute wheel picker
  (`WheelPicker`); edit or delete an existing departure from the schedule list's edit dialog.
- **Next departure** — every route card shows its next departure time; the schedule list highlights
  the next departure with a `NEXT` badge and shows a countdown banner. Both screens re-render at the
  top of every minute so the displayed "next" departure stays correct.
- **Favorites & default route** — mark routes as favorite and choose one default route. The route
  list is ordered default → favorite → newest.
- **Home-screen widget** — a `RemoteViews` collection widget lists all routes with their next
  departure time. Tapping a row opens that route's schedule list; the widget refreshes on data
  changes, when the app comes to the foreground, and through an alarm scheduled around the next
  departure time.
- **Import / export** — copy all data to the clipboard as Base64-encoded JSON (`version: 2`) and
  import it back. Import validates fields and `HH:mm` times, skips invalid entries, merges duplicate
  routes, keeps at most one default route and reports imported/skipped counts.
- **Dark mode** — the app uses the Material3 DayNight theme with theme attributes; the widget ships
  its own light/night color sets. No screen relies on hardcoded light-only colors.
- **Edge-to-edge UI** — transparent system bars with manual window-inset padding.
- **Accessibility** — toolbar navigation exposes a content description; text colors follow the
  current theme.

No runtime permissions are requested. The only declared components are `MainActivity`, the widget
provider and the widget's `RemoteViewsService`.

## Tech Stack

| Category        | Value                                                                         |
| --------------- | ----------------------------------------------------------------------------- |
| Language        | Kotlin (all production/test code, including Room entities); JVM target 11      |
| Kotlin toolchain| AGP 9 built-in Kotlin 2.2.10 — no `kotlin-android` plugin; Room via KSP          |
| UI toolkit      | Material3 1.13.0, AppCompat 1.7.1, RecyclerView 1.4.0, Fragment 1.8.9, LiveData 2.10.0 |
| Persistence     | Room 2.8.4 processed with KSP 2.2.10-2.0.2                                      |
| Time picker     | `WheelPicker` (JitPack: `com.github.open-android:WheelPicker:v1.0.0`)          |
| Build           | Gradle 9.4.1 (wrapper), Android Gradle Plugin 9.0.0, JDK 17 for Gradle         |
| SDK levels      | minSdk 29 (Android 10) · targetSdk 36 · compileSdk 36                          |
| Tests           | JUnit 4.13.2 (JVM) + AndroidX Test/Espresso (instrumented, template only)      |
| CI              | GitHub Actions — build/test/lint on `main` and PRs, signed release on `v*` tags |

## Getting Started

### Prerequisites

- JDK 17 for Gradle (Kotlin/Java bytecode target is 11).
- Android SDK with **Platform 36** and **Build-Tools 36.0.0** installed.
- A device or emulator running API 29+.

The custom `WheelPicker` dependency is resolved from JitPack, so the first build needs network
access to `https://jitpack.io`.

### Build

```bash
./gradlew assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease         # app/build/outputs/apk/release/
./gradlew test                    # JVM unit tests
./gradlew lintDebug               # Android lint
./gradlew connectedAndroidTest    # instrumented tests (needs a device/emulator)
```

Release builds are **unsigned by default**: the repository contains no keystore. To sign locally,
provide all four environment variables; when `RELEASE_KEYSTORE_FILE` is unset the release build type
stays unsigned:

```bash
RELEASE_KEYSTORE_FILE=/absolute/path/to/release.jks \
RELEASE_STORE_PASSWORD='...' \
RELEASE_KEY_ALIAS='...' \
RELEASE_KEY_PASSWORD='...' \
./gradlew assembleRelease
```

### Version strategy

`versionCode = 100000` / `versionName = "1.0.0"` are intentionally static. A release is an explicit
step: bump both values in `app/build.gradle`, commit, then push a `v*` tag. The tag name drives the
published artifact name (`Bus_<tag>.apk`).

## Architecture

Single-Activity app with manually managed child fragments. There is no ViewModel, no DI framework and
no Navigation Component.

All production, unit-test and instrumented-test code is Kotlin (`.kt`) — there are no `.java` sources.
Kotlin sources use the standard `src/main/java`, `src/test/java` and `src/androidTest/java`
directories.

```
MainActivity (edge-to-edge, launchMode=singleTop)
  └── QueryFragment (navigation host: child FragmentManager + manual back stack)
        ├── RouteListFragment        route list, next departures, import/export, long-press menu
        ├── ScheduleListFragment     schedule list, countdown banner, edit dialog, minute ticker
        ├── AddEditRouteFragment     add/edit route form
        └── AddEditScheduleFragment  add a departure time with the wheel picker

Home screen
  └── BusWidgetProvider (AppWidgetProvider) + BusWidgetService (RemoteViewsService)
        ├── res/layout/widget_bus.xml
        └── res/layout/widget_bus_item.xml
```

### Package layout

```
com.senk.bus
├── MainActivity.kt                  single Activity, edge-to-edge, widget-intent handling
├── data/
│   ├── AppDatabase.kt               Room singleton ("bus_database", schema v1)
│   ├── AppExecutors.kt              single-thread disk IO executor ("bus-disk-io")
│   ├── NextDeparture.kt             pure-Kotlin HH:mm / next-departure helpers (unit tested)
│   ├── dao/
│   │   ├── RouteDao.kt              route CRUD, favorites, default, sync list
│   │   └── ScheduleDao.kt           schedule CRUD, per-route and all-schedules sync queries
│   └── entity/
│       ├── Route.kt                   Kotlin @Entity: id, name, origin, destination, flags
│       └── Schedule.kt                Kotlin @Entity: id, routeId (FK → CASCADE, indexed), time
├── ui/
│   ├── QueryFragment.kt             child-fragment navigation host (500 ms navigation debounce)
│   ├── RouteListFragment.kt         route list + minute ticker + import/export
│   ├── ScheduleListFragment.kt      schedule list + banner + minute ticker + edit dialog
│   ├── AddEditRouteFragment.kt      route add/edit form
│   ├── AddEditScheduleFragment.kt   schedule add screen (edit uses the list dialog)
│   ├── WheelTimePicker.kt           wraps two WheelPicker views into an hour:minute picker
│   └── adapter/
│       ├── RouteAdapter.kt          route cards (DiffUtil)
│       └── ScheduleAdapter.kt       schedule rows + "NEXT" badge (DiffUtil)
└── widget/
    ├── BusWidgetProvider.kt         AppWidgetProvider: RemoteViews, click templates, alarm refresh
    └── BusWidgetService.kt          RemoteViewsService: list rows and next-departure times
```

### Data model

```
routes
├── id             INTEGER  (primary key, auto-generated)
├── name           TEXT     (not null)
├── origin         TEXT     (not null)
├── destination    TEXT     (not null)
├── isFavorite     INTEGER  (boolean)
└── isDefault      INTEGER  (boolean)

schedules
├── id             INTEGER  (primary key, auto-generated)
├── routeId        INTEGER  (FK → routes.id, ON DELETE CASCADE, indexed)
└── departureTime  TEXT     (strict HH:mm, default "00:00")
```

Room schema version is **1** and `exportSchema = false`; a future schema change must bump the version
and add a `Migration` to `AppDatabase`. `android:allowBackup` is enabled, so the database is included
in Android auto-backup by default.

### Next-departure calculation

All "next departure" math lives in `com.senk.bus.data.NextDeparture` (pure Kotlin, no Android
dependencies, covered by JVM tests in
`app/src/test/java/com/senk/bus/data/NextDepartureTest.kt` — 13 cases):

- `nowHHmm()`, `isValid(String)`, `parseMinutes(String)`, `format(int)`, `minutesUntil(from, to)` —
  all midnight-wrap aware; invalid times return `-1` instead of throwing;
- `find(nowHHmm, schedules)` — returns `time`, `remainingMinutes` and the matching `Schedule`, or
  `null` when no valid entry exists. It is order-independent and skips invalid entries; when every
  valid time has already passed it returns tomorrow's earliest departure.

DAOs return schedules in `departureTime ASC` order for display, but callers must not rely on ordering
for correctness — use `NextDeparture.find(...)` instead of raw string comparison.

### Threading model

`AppExecutors.diskIO(...)` is a single-thread executor (named `bus-disk-io`) used for every Room
read/write that does not return `LiveData`. Room `LiveData` queries observe on the main thread.

Background work must never call `requireContext()`, `requireActivity()`, `getString()` or touch
views. Capture `final Context appContext = requireContext().getApplicationContext()` and any strings
or data on the main thread first; return to the UI with `runOnUiThread`/`view.post` and guard with
`isAdded()`/`getView() != null` before touching views.

### UI behaviour details

- `RouteListFragment` recomputes per-route next departures at the top of each minute (and immediately
  after the list changes).
- `ScheduleListFragment` re-sorts schedules by proximity to "now" every minute, keeps the `NEXT`
  badge attached to the actual next schedule id, and shows a countdown banner.
- `QueryFragment` debounces navigation entries by 500 ms and pops the child back stack synchronously
  (`popBackStackImmediate`), so fast double taps/back presses cannot skip a level.
- Save/delete actions show feedback only after the database write succeeds; failures keep the page
  and inputs intact.

## Home-Screen Widget

`BusWidgetProvider` (declared in `AndroidManifest.xml`) renders a scrollable list with
`BusWidgetService` (`RemoteViewsService`).

- Rows show route name, origin → destination and the next departure time. All routes are listed in
  the same order as the app (default → favorite → newest id).
- Row clicks use an explicit `PendingIntent` to `MainActivity` with the collection template created
  as `FLAG_MUTABLE` (required for fill-in intents) plus `FLAG_ACTIVITY_SINGLE_TOP |
  CLEAR_TOP`; `MainActivity` is `launchMode="singleTop"`, handles `onNewIntent`, looks the route up
  in the database (showing a toast when it no longer exists) and then opens the schedule list.
- Data is refreshed via `BusWidgetProvider.notifyDataChanged(...)` after data mutations and when the
  app resumes. In addition, an `AlarmManager` alarm (`setAndAllowWhileIdle` — inexact, no
  exact-alarm permission) fires a `WIDGET_REFRESH` broadcast one second after the earliest next
  departure and reschedules itself. If no departure exists, or the query fails, a 30-minute fallback
  refresh is used. `updatePeriodMillis` is 30 minutes as an extra safety net.
- Widget colors live in `res/values/widget_colors.xml` + `res/values-night/widget_colors.xml`;
  widget-only strings live in `res/values/strings_widget.xml` (labels shared with the app stay in
  `strings.xml`). Every widget `TextView` sets an explicit color so RemoteViews do not depend on a
  theme context.
- `bus_widget_info.xml` provides `previewLayout` and `targetCellWidth/Height` (4×2). The collection
  APIs (`setRemoteAdapter(Intent)`, `notifyAppWidgetViewDataChanged`) are deprecated since API 31/37
  but still work; migrating to `RemoteCollectionItems` is a known follow-up.

## Import / Export

All data is copied to the system clipboard as Base64-encoded JSON. Export uses `Base64.NO_WRAP`;
import tolerates `Base64.DEFAULT` (line-wrapped input). The JSON root contains a version field:

```json
{
  "version": 2,
  "routes": [
    {
      "name": "Shuttle A",
      "origin": "Office",
      "destination": "Station",
      "isFavorite": true,
      "isDefault": false,
      "schedules": [
        { "departureTime": "08:30" },
        { "departureTime": "17:00" }
      ]
    }
  ]
}
```

Import behaviour (implemented in `RouteListFragment`):

- clipboard content is read defensively (missing/non-text clip → import error);
- routes with an empty name/origin/destination are skipped and counted;
- departure times must match strict `HH:mm` (`NextDeparture.isValid`); invalid times are skipped and
  counted (they are never persisted);
- the whole import runs inside one `db.runInTransaction(...)`; live data is refreshed once afterwards
  and the widget is notified;
- duplicate routes (same name + origin + destination) are merged: the existing route is reused and
  only departure times that do not already exist are inserted; re-importing the same payload is
  idempotent;
- at most one route keeps `isDefault = true` (existing defaults are cleared first, the first
  imported default wins); `isFavorite` is only ever set to true while merging;
- the result toast reports imported routes / skipped entries (`import_result`).

`version` is currently written for forward compatibility only — the importer accepts any payload that
contains a valid `routes` array and ignores the field. Bump the version when the format changes.

## Dark Mode & Resource Conventions

- `Theme.Bus` inherits `Theme.Material3.DayNight.NoActionBar`; Material3 supplies the light/dark
  palettes, so no `values-night/themes.xml` is needed.
- Layouts use theme attributes (`?attr/colorOnSurface`, `?attr/colorSurface`,
  `?attr/colorOnSurfaceVariant`, `?attr/colorPrimary`, `?attr/colorError`) instead of hardcoded
  colors. The `NEXT` badge is the one exception: it keeps white text on its fixed green background
  for contrast.
- Concrete colors only needed outside a theme context (e.g. the dialog background) live in
  `values/colors.xml` with a matching night value in `values-night/colors.xml`.
- The launcher icon is a single adaptive icon in `res/mipmap-anydpi-v26/` with a `<monochrome>`
  layer. The `-v26` qualifier is required for `<adaptive-icon>` XML — moving it to `mipmap-anydpi`
  makes AAPT fail to link `@mipmap/ic_launcher`.
- User-visible strings live in `values/strings.xml`, accessibility-only strings in
  `values/strings_accessibility.xml`, widget-only strings in `values/strings_widget.xml`.

## CI/CD

`.github/workflows/android.yml` (least-privilege `contents: read`, release job elevates to
`contents: write`; concurrency cancels superseded runs):

| Trigger | What runs |
| --- | --- |
| Push to `main`, pull request targeting `main` | JDK 17 + Android SDK, `sdkmanager "platforms;android-36" "build-tools;36.0.0"`, `./gradlew --no-daemon assembleDebug test lintDebug`, upload `debug-apk` artifact (7 days) |
| Push of a `v*` tag | Runs the build job first, then decodes `RELEASE_KEYSTORE_BASE64` to a temp keystore, runs `./gradlew --no-daemon assembleRelease` with the signing env vars, copies the APK to `dist/Bus_<tag>.apk`, publishes it with `softprops/action-gh-release@v2` |

Repository secrets required by the release job:

| Secret | Purpose |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded release keystore (`.jks`) |
| `RELEASE_STORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias inside the keystore |
| `RELEASE_KEY_PASSWORD` | Key password |

Creating the keystore secret:

```bash
base64 -w 0 release.jks        # Linux/macOS
# PowerShell:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))
```

## Known Limitations / Follow-ups

The current code intentionally keeps a small, dependency-light architecture; the following are known
and accepted for now:

- **No ViewModel/state layer**: dialogs and in-progress form state are not preserved across
  configuration changes; navigation is manual (no Navigation Component).
- **Room evolution**: `exportSchema = false`, no migration tests and no unique/CHECK constraints on
  schedules. Adding them requires a DB version bump + migration.
- **Widget APIs**: collection `RemoteViews` APIs are deprecated; per-widget configuration (each
  instance showing a fixed route) is not implemented, and the alarm is inexact (may be delayed in
  Doze).
- **Testing**: only `NextDepartureTest` has real coverage; instrumented tests are still the Android
  Studio template.
- **Kotlin + KSP toolchain**: Room is processed with KSP. KSP currently registers its generated
  sources through the Kotlin source-set DSL, so `gradle.properties` sets the experimental
  `android.disallowKotlinSourceSets=false`; remove it once KSP supports AGP 9 built-in Kotlin without
  that flag.
- **Lint**: build is clean (0 errors) but lint reports warnings for newer library/SDK versions and one
  false-positive `ObsoleteSdkInt` for the required `mipmap-anydpi-v26` folder.
- **Packaging**: `versionCode`/`versionName` are static (bumped manually at release time); the app is
  Chinese-only (no translated resource folders); `WheelPicker` comes from JitPack.
- **No screenshots** are stored in the repository yet.

## Documentation

| File | Contents |
| --- | --- |
| `README.md` | This file — project overview (English) |
| `docs/README_CN.md` | Chinese translation of this README |
| `AGENTS.md` | AI/agent guidance for working in this repository |
| `LICENSE` | MIT license |

Local-only artifacts are excluded from version control: historical audit/fix reports are kept in
`.reports/` (git-ignored), and IDE/tooling folders (`.idea/`, `.gradle/`, `.kotlin/`, `.claude/`) are
ignored as well.

## License

[MIT](LICENSE)

---

[中文版](docs/README_CN.md)
