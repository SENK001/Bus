# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android bus schedule query app ("班车查询") — a single-module Java app for viewing and managing bus/shuttle routes and departure times. Package: `com.senk.bus`.

## Build & Run

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK (R8 minification disabled)
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
```

Gradle 9.4.1, Android Gradle Plugin 9.0.0-alpha06, Java 11, minSdk 29, targetSdk 36.

## Architecture

Single-Activity (`MainActivity`) + Fragment navigation. No ViewModel, no Hilt/Dagger, no Navigation Component.

- **Persistence**: Room database (`AppDatabase`, DB name `"bus_database"`, version 1) with two tables: `routes` and `schedules`. Accessed via singleton `AppDatabase.getInstance(context)`.
- **Threading**: `AppExecutors` provides a single-thread executor for disk I/O. DAO methods returning `LiveData` auto-observe on the main thread; mutations must be dispatched through `AppExecutors.diskIO().execute(...)`.
- **Navigation**: `QueryFragment` acts as a container managing child fragment transactions and back stack manually (no NavComponent).
- **Reactive data**: `LiveData` from Room DAOs is observed directly in fragments — no ViewModel layer.

### Data Model

- `Route` — `id` (auto-generated), `name`, `origin`, `destination`, `isFavorite`, `isDefault` (boolean flags)
- `Schedule` — `id` (auto-generated), `routeId` (FK → Route with CASCADE delete), `departureTime`

### Package Layout

```
com.senk.bus
├── MainActivity.java
├── data/
│   ├── AppDatabase.java          # Room DB singleton
│   ├── AppExecutors.java         # Single-thread disk IO executor
│   ├── dao/
│   │   ├── RouteDao.java         # Route CRUD, favorites, default management
│   │   └── ScheduleDao.java      # Schedule CRUD per route
│   └── entity/
│       ├── Route.java
│       └── Schedule.java
└── ui/
    ├── QueryFragment.java        # Navigation host (manages child fragment backstack)
    ├── RouteListFragment.java    # Route list with next-departure display, FAB to add
    ├── ScheduleListFragment.java # Schedule list with next-departure banner + auto-refresh
    ├── AddEditRouteFragment.java # Add/edit route form
    ├── AddEditScheduleFragment.java # Add/edit schedule using MaterialTimePicker
    └── adapter/
        ├── RouteAdapter.java     # RecyclerView adapter for routes
        └── ScheduleAdapter.java  # RecyclerView adapter with "NEXT" badge
```

## Key Conventions

- **Language**: Java (not Kotlin). Entities are POJOs with public fields.
- **UI strings**: Chinese (zh). String resources in `res/values/strings.xml`.
- **No ProGuard/R8** rules configured — minification is disabled.
- **No lint configuration** beyond defaults.
- **Naming**: `*Fragment`, `*Adapter`, `*Dao`, `*Entity`.
