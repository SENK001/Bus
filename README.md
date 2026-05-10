# Bus Schedule（班车查询）

An Android app for viewing and managing bus/shuttle routes and departure times.

## Features

- **Route management** — create, edit, and delete bus routes with a name, origin, and destination.
- **Schedule management** — add, edit, and delete departure times for each route using a wheel time picker.
- **Next departure display** — each route card shows the upcoming departure time; schedule lists auto-refresh every 60 seconds.
- **Favorites & default route** — mark frequently used routes and set a default that sorts to the top.
- **Import/Export** — copy all data to the clipboard as Base64-encoded JSON, or import from clipboard.
- **Dark mode** — Material3 DayNight theme adapts automatically.
- **Edge-to-edge** — system bars are transparent with proper padding insets.

## Screenshots

<!-- TODO: add screenshots -->

## Tech Stack

| Category       | Technology                                              |
| -------------- | ------------------------------------------------------- |
| Language       | Java 11                                                 |
| UI             | Android Material3, RecyclerView, LiveData               |
| Persistence    | Room 2.8.4                                              |
| Time picker    | WheelPicker (JitPack: `com.github.open-android`)        |
| Build          | Gradle 9.4.1, Android Gradle Plugin 9.0.0-alpha06       |
| Min SDK        | API 29 (Android 10)                                     |
| Target SDK     | API 36                                                  |

## Getting Started

### Prerequisites

- Android Studio or JDK 17+ with the Android SDK installed
- A device or emulator running API 29+

### Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK
```

### Test

```bash
./gradlew test                   # unit tests
./gradlew connectedAndroidTest   # instrumented tests (requires device)
```

## Architecture

**Single-Activity** — `MainActivity` hosts a `QueryFragment` container that manages child fragment backstack navigation manually. No Navigation Component, no DI framework, no ViewModel layer.

```
MainActivity (edge-to-edge)
  └── QueryFragment (navigation host)
        ├── RouteListFragment        ← route list + import/export
        ├── ScheduleListFragment     ← schedules view + auto-refresh banner
        ├── AddEditRouteFragment     ← add/edit route form
        └── AddEditScheduleFragment  ← wheel time picker for departure
```

### Package Structure

```
com.senk.bus
├── MainActivity.java
├── data/
│   ├── AppDatabase.java            Room singleton ("bus_database", v1)
│   ├── AppExecutors.java           Single-thread disk IO executor
│   ├── dao/
│   │   ├── RouteDao.java           Route CRUD, favorites, default management
│   │   └── ScheduleDao.java        Schedule CRUD per route
│   └── entity/
│       ├── Route.java              id, name, origin, destination, isFavorite, isDefault
│       └── Schedule.java           id, routeId (FK → CASCADE), departureTime
└── ui/
    ├── QueryFragment.java          Child-fragment navigation host
    ├── RouteListFragment.java      Route list, import/export, long-press menu
    ├── ScheduleListFragment.java   Schedule list with scroll-aware banner
    ├── AddEditRouteFragment.java   Route add/edit form
    ├── AddEditScheduleFragment.java   Schedule add/edit with WheelPicker
    ├── WheelTimePicker.java        Wraps WheelPicker for hour:minute selection
    └── adapter/
        ├── RouteAdapter.java       RecyclerView adapter for routes
        └── ScheduleAdapter.java    RecyclerView adapter with "NEXT" badge
```

### Data Model

```
routes
├── id             INTEGER (PK, auto)
├── name           TEXT    (not null)
├── origin         TEXT    (not null)
├── destination    TEXT    (not null)
├── isFavorite     INTEGER (boolean)
└── isDefault      INTEGER (boolean)

schedules
├── id             INTEGER (PK, auto)
├── routeId        INTEGER (FK → routes.id, ON DELETE CASCADE)
└── departureTime  TEXT    (format HH:mm, default "00:00")
```

### Threading

`AppExecutors.diskIO(Runnable)` provides a single-thread executor for Room write operations. `LiveData` queries from Room automatically observe on the main thread.

## Import/Export

Data is exported as Base64-encoded JSON to the system clipboard. Import reads from the clipboard and inserts all routes with their schedules into the database.

**JSON format:**

```json
{
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

## CI/CD

GitHub Actions (`v*` tag push) builds a release APK, signs it, and uploads to GitHub Releases.

## License

[MIT](LICENSE)

---

[中文版](README_CN.md)
