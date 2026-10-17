# AGENTS.md — Agent Instructions

Guidance for AI coding agents working in this repository. Read this before editing code.

> Naming note: this file was previously `CLAUDE.md` and is now named `AGENTS.md` following the
> cross-tool convention. Most agent tools auto-load `AGENTS.md`; any tool that only reads
> `CLAUDE.md` needs an explicit pointer (symlink/copy or a one-line `CLAUDE.md` reference).

## Project Snapshot

Android bus/shuttle schedule app ("班车查询", package `com.senk.bus`). Single-module Gradle
project (`:app`). It lists routes, manages departure times, shows the next departure, supports
clipboard import/export, and ships a home-screen widget. The UI is Simplified Chinese only.

| Item | Value |
| --- | --- |
| Min / target / compile SDK | 29 / 36 / 36 |
| Gradle / AGP | 9.4.1 (wrapper) / 9.0.0 |
| Gradle JVM | JDK 17 (`JAVA_HOME`); Kotlin/Java bytecode target 11 |
| Kotlin | All production/test code is Kotlin; compiled by AGP 9 built-in Kotlin 2.2.10, Room via KSP 2.2.10-2.0.2 |
| Dependencies | Room 2.8.4, Material 1.13.0, AppCompat 1.7.1, Fragment 1.8.9, LiveData 2.10.0, WheelPicker (JitPack) |
| Package | `com.senk.bus` |
| Version | `versionCode = 100000`, `versionName = "1.0.0"` (static; bumped only for releases) |

## Repository Layout

```
README.md                 project overview (English)
docs/README_CN.md         project overview (Chinese)
AGENTS.md                 this file (agent guidance)
LICENSE                   MIT
app/                      application module
  src/main/java/…         Kotlin sources (production code + Room entities)
  src/test/java/…         JVM unit tests (NextDepartureTest)
  src/androidTest/…       instrumented test template
  build.gradle            SDK levels, signing config, dependencies
build.gradle              AGP version
settings.gradle           repositories (JitPack for WheelPicker)
.github/workflows/…       CI (build/test/lint + signed release on tags)
.gitattributes            normalizes line endings (LF, CRLF for *.bat)
.reports/                 local-only historical reports — git-ignored, never commit
```

## Build, Test & Verify

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # unsigned unless RELEASE_* env vars are provided
./gradlew test                   # JVM unit tests (NextDepartureTest, 13 cases)
./gradlew lintDebug              # Android lint
./gradlew connectedAndroidTest   # instrumented tests (device/emulator required)
```

- A change is not done until `./gradlew assembleDebug test lintDebug` passes locally.
- Release signing is environment-driven: `RELEASE_KEYSTORE_FILE` +
  `RELEASE_STORE_PASSWORD` + `RELEASE_KEY_ALIAS` + `RELEASE_KEY_PASSWORD`. Without
  `RELEASE_KEYSTORE_FILE` the release build stays unsigned.
- CI (`.github/workflows/android.yml`): `main` pushes/PRs run `assembleDebug test lintDebug`;
  `v*` tags additionally build a signed release and publish `Bus_<tag>.apk` to GitHub Releases.
  Required secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`,
  `RELEASE_KEY_PASSWORD`.
- Current lint baseline is **0 errors** (only warnings about newer library/SDK versions and one
  `ObsoleteSdkInt` false positive for `mipmap-anydpi-v26`). Do not introduce new errors.
- Do not commit `versionCode`/`versionName`, SDK-level or AGP/Gradle changes as part of unrelated
  work — those are deliberate release steps.

## Architecture

Single-Activity + manually managed child fragments. **No** ViewModel, no DI (Hilt/Dagger), no
Navigation Component. All production and test code is Kotlin (`.kt`); the module has no Java sources.

```
MainActivity (edge-to-edge, launchMode=singleTop)
  └── QueryFragment (navigation host: child FragmentManager + manual back stack)
        ├── RouteListFragment
        ├── ScheduleListFragment
        ├── AddEditRouteFragment
        └── AddEditScheduleFragment   (add only; edits happen in the list dialog)

BusWidgetProvider (AppWidgetProvider) + BusWidgetService (RemoteViewsService)
```

### Packages

```
com.senk.bus
├── MainActivity.kt
├── data/
│   ├── AppDatabase.kt      Room singleton ("bus_database", version 2, exportSchema = true)
│   ├── AppExecutors.kt     single-thread disk IO ("bus-disk-io")
│   ├── NextDeparture.kt    pure-Kotlin HH:mm helpers (unit tested)
│   ├── dao/                  RouteDao, ScheduleDao
│   └── entity/               Route.kt, Schedule.kt (Kotlin)
├── ui/                       fragments, WheelTimePicker, adapters
└── widget/                   BusWidgetProvider, BusWidgetService
```

### Data model

- `routes`: `id` (PK, long), `name`, `origin`, `destination`, `isFavorite`, `isDefault`.
- `schedules`: `id` (PK, long), `routeId` (FK → `routes.id`, `ON DELETE CASCADE`, indexed),
  `departureTime` (strict `HH:mm` string).
- Schema version is **2** and `exportSchema = true`. Changing the schema requires a version bump
  plus a `Migration` in `AppDatabase`; Room migration testing is available.
- `android:allowBackup` is on, so the database is included in Android auto-backup.

## Hard Rules (common sources of bugs)

### Threading & lifecycle
- Never call `requireContext()`, `requireActivity()`, `getString()` or touch views from an
  `AppExecutors.diskIO(...)` task. Capture `final Context appContext = requireContext()
  .getApplicationContext()` and any strings/data on the main thread first.
- Return to the UI with `runOnUiThread`/`view.post` and guard with `isAdded()`/`getView() != null`
  before touching views. The executor logs uncaught exceptions but a crash there means a lost
  operation — handle expected failures explicitly.
- Start `Handler` tickers in `onResume`, remove callbacks in `onPause`/`onDestroy`.
- Keep dialog callbacks (`AlertDialog`) guarded against repeated submit and fragment detachment;
  dialogs are not `DialogFragment`s (known limitation).

### Next departure
- Every "next departure" computation must go through `com.senk.bus.data.NextDeparture`; never
  re-implement HH:mm math or compare time strings.
- `NextDeparture.isValid` is strict `HH:mm`; invalid input returns `-1` from `parseMinutes` /
  `minutesUntil` instead of throwing.
- `NextDeparture.find(nowHHmm, schedules)` is order-independent, skips invalid entries, and wraps to
  tomorrow's earliest when all valid times have passed. `remainingMinutes` is `0..1439`.
- `ScheduleDao` returns schedules in `departureTime ASC` order for display, but correctness must not
  depend on that ordering.

### Navigation
- `QueryFragment` owns navigation: `navigateToXxx(...)` methods, 500 ms debounce, child back stack.
- Back handling uses `popBackStackImmediate()` after checking `isStateSaved()`. Do not add async
  `commit()` + immediate `popBackStack()` pairs.
- `MainActivity` is `launchMode="singleTop"`; widget clicks are delivered via `onNewIntent` and
  validated against the database (`routeId` must still exist) before navigating.

### Import / Export
- Export: `Base64.NO_WRAP` of JSON `{"version":2,"routes":[{name,origin,destination,isFavorite,
  isDefault,schedules:[{departureTime}]}]}`.
- Import: tolerate `Base64.DEFAULT`, read the clipboard defensively, validate fields and strict
  `HH:mm` times, skip/count invalid entries, run the whole import inside
  `db.runInTransaction(...)`, merge duplicate routes by name+origin+destination, insert only missing
  departure times (idempotent), keep at most one `isDefault = true`, and report imported/skipped
  counts. The `version` field is written for forward compatibility but is not enforced on import.
- Clipboard work must not run on the main thread beyond the initial text read.

### Widget
- `BusWidgetProvider` renders a collection via `BusWidgetService`; only `RemoteViews`-supported
  views/attributes may be used (no custom views, data binding or `android:foreground` in widget
  layouts).
- Collection click template PendingIntent must be `FLAG_MUTABLE` (`FLAG_IMMUTABLE` silently drops
  fill-in extras) and use an explicit `MainActivity` intent; fill-in intents should carry only ids.
- Refresh data with `BusWidgetProvider.notifyDataChanged(context)` after mutations; the provider also
  schedules an inexact `AlarmManager.setAndAllowWhileIdle` alarm at the next departure time
  (+1 s) and falls back to 30 minutes. `updatePeriodMillis` is 30 minutes.
- Widget colors must be explicit resources (`values/widget_colors.xml` +
  `values-night/widget_colors.xml`); widget-only strings go to `values/strings_widget.xml`.
- Collection APIs (`setRemoteAdapter(Intent)`, `notifyAppWidgetViewDataChanged`) are deprecated but
  intentionally kept; do not switch APIs casually without verifying widget behaviour on a device.

### UI resources
- Prefer Material3 theme attributes (`?attr/colorOnSurface`, `?attr/colorSurface`,
  `?attr/colorOnSurfaceVariant`, `?attr/colorPrimary`, `?attr/colorError`). Do not hardcode colors
  in layouts; `NEXT` badge keeps white on its fixed green background by design.
- Concrete non-theme colors go in `values/colors.xml` with a matching night value in
  `values-night/colors.xml`.
- Toolbars: `MaterialToolbar` with `?attr/homeAsUpIndicator` navigation icon and
  `app:navigationContentDescription="@string/navigate_back"`. Do not tint titles/navigation
  manually — theme defaults handle light/dark.
- Strings: user-visible text in `values/strings.xml`; accessibility-only text in
  `values/strings_accessibility.xml`; widget-only text in `values/strings_widget.xml`.
- No FAB: the add action lives in the toolbar menu (`menu_main.xml`, `menu_schedule_list.xml`,
  `menu_add_edit.xml`).
- Time picker is the JitPack `WheelPicker` wrapped by `WheelTimePicker`; there is no
  `MaterialTimePicker`.
- Launcher icon lives in `res/mipmap-anydpi-v26/` (adaptive icon + `<monochrome>`). The `-v26`
  qualifier is mandatory for `<adaptive-icon>`; `mipmap-anydpi` breaks AAPT linking.

### Language / build
- All code is **Kotlin** (`*.kt`): production, JVM unit tests and instrumented tests. Do not add
  `.java` sources. Kotlin is compiled by AGP 9's built-in Kotlin support (2.2.10) — do not add the
  `kotlin-android` plugin or an explicit Kotlin stdlib dependency.
- Room entities are Kotlin classes with `var` properties, DAOs are Kotlin abstract
  classes/interfaces, and Room is processed with **KSP** (`id 'com.google.devtools.ksp'`,
  `ksp 'androidx.room:room-compiler:2.8.4'`, KSP 2.2.10-2.0.2). Do not reintroduce
  `annotationProcessor` for Room.
- Use idiomatic Kotlin (`val`/`var`, lambdas, `object :` for anonymous classes) while keeping
  behaviour identical. Dispatch disk work with `AppExecutors.diskIO { ... }`.
- Kotlin/Java bytecode target is 11 (matching `compileOptions`).
- Use Gradle `=` assignments (`minSdk = 29`, `url = '...'`) — space-assignment syntax is removed in
  Gradle 10.
- `androidx.constraintlayout` was removed because no layout uses it; do not reintroduce it.
- Do not change SDK levels, AGP/Gradle versions, or the version code/name in unrelated changes.

## Docs & Repository Hygiene

- User-facing docs live in `README.md` (English, root) and `docs/README_CN.md` (Chinese).
  `AGENTS.md` is the agent guide; `LICENSE` is MIT. Keep `docs/` for documentation — do not add
  ad-hoc markdown files at the repository root.
- Temporary reports (audits, fix reports, one-off analyses) go to `.reports/`, which is git-ignored.
  Never commit files from `.reports/`, `.claude/`, `.idea/`, `.gradle/`, `.kotlin/`, `build/` or
  generated output.
- Keep line endings normalized via `.gitattributes` (`* text=auto eol=lf`; `*.bat`/`*.cmd` CRLF).
- The Gradle wrapper must stay executable in git (`100755` for `gradlew`); CI also runs `chmod +x`.

## Testing

- `app/src/test/java/com/senk/bus/data/NextDepartureTest.kt` — 13 JVM tests covering parsing,
  formatting, midnight wrap and `find(...)` edge cases. Extend it when touching time logic.
- `ExampleUnitTest` / `ExampleInstrumentedTest` are Android Studio templates; do not treat them as
  coverage.
- Prefer pure-Kotlin helpers (no Android imports) so logic stays unit-testable without Robolectric.

## Known Limitations (accepted, do not "fix" silently)

- ViewModel adoption is partial: `AddEditRouteViewModel` keeps route form state across
  configuration changes, but most screens still rely on Fragment-local state.
- Manual navigation; no Navigation Component.
- Room: schema export + migration tests are in place, and schedules enforce a per-route
  unique departure-time constraint.
- Deprecated widget collection APIs; no per-widget configuration; inexact widget alarm.
- Kotlin + KSP: KSP registers generated sources through the Kotlin source-set DSL, so
  `gradle.properties` keeps `android.disallowKotlinSourceSets=false` (experimental) until KSP
  supports AGP 9 built-in Kotlin natively.
- Static version numbers; Chinese-only resources; JitPack-hosted `WheelPicker`.
- Lint warnings for newer dependency/SDK versions and one `mipmap-anydpi-v26` false positive are
  accepted.

## Before Finishing a Change

1. `./gradlew assembleDebug test lintDebug` passes (no new lint errors).
2. Behavior changes are reflected in `README.md` and `docs/README_CN.md` (both languages).
3. No files outside the intended scope changed; `git status` shows only expected paths.
4. No debug leftovers, hardcoded user-facing strings, or `TODO` placeholders introduced.
5. Widget/import-export/navigation changes: mention what could not be verified without a device.
