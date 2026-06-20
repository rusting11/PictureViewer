# PictureViewer — Agent Guide

## Build Commands (Windows PowerShell)

```
.\gradlew.bat assembleDebug      # debug APK
.\gradlew.bat assembleRelease    # release APK (unsigned)
.\gradlew.bat test               # unit tests
```

No separate lint/typecheck steps — Kotlin compilation catches type errors.
Gradle configuration cache is enabled (`gradle.properties`).

## Architecture

Single-module Android app (`app/`). Package: `com.example.pictureviewer`.

- **Entry**: `MainActivity` → Compose `NavHost` with two routes: `library` → `reader/{comicUri}`
- **Library**: `LibraryScreen` / `LibraryViewModel` — folder scanning, comic list display
- **Reader**: `ReaderScreen` / `ReaderViewModel` — vertical (LazyColumn) and horizontal (HorizontalPager) reading modes
- **Data**: `DataStore` (SharedPreferences + Gson), `LibraryEntry`, `ReadingProgress`
- **Components**: `ZoomableImage` (Coil AsyncImage + pinch-to-zoom via `awaitEachGesture`)
- **Theme**: `AppTheme` with custom colors in `ui/theme/`

## Key Libraries

- **Image loading**: Coil (`coil-compose`), configured in `PictureViewerApplication`
- **Serialization**: Gson (SharedPreferences-based, not Room/DataStore)
- **Navigation**: `navigation-compose` with URL-encoded comic URIs
- **Compose BOM**: `2024.12.01`, Material3

## Conventions

- All Compose UI is in Kotlin files under `ui/` — no XML layouts
- `AppTheme.colors` used for theming (custom `AppColors`, not Material `ColorScheme`)
- No Hilt/Dagger — ViewModels use default `viewModel()` factory
- `DataStore` is a custom singleton (not AndroidX DataStore)
- URI encoding/decoding used for navigation arguments (`URLEncoder.encode`)

## Gotchas

- `Icons.Rounded.LastPage` is deprecated — use `Icons.AutoMirrored.Rounded.LastPage`
- `ZoomableImage` uses `awaitEachGesture` (not `detectTransformGestures`) to avoid consuming single-finger scroll events in `LazyColumn`
- Reader scroll state update deferred to `isScrolling == false` to prevent fling interruption
- Release APK is unsigned by default — no signing config in `build.gradle.kts`
- `compileSdk = 35`, `minSdk = 26`, Java 11 source/target compatibility
