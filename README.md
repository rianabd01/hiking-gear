# Hiking Gear

Offline hiking navigation app for iOS built with Kotlin Multiplatform and Compose Multiplatform.

## Features

- **Maps & GPX**: open `.gpx` files, switch map styles (Standard, Topo, Satellite, Dark), and download map regions for offline use with MapLibre.
- **Geospatial PDF**: open georeferenced PDFs (ISO 32000-1 & TerraGo) via PDFKit with live GPS position and GPX route overlays.
- **Hike Recording**: record live tracks (duration, distance, elevation), save locally as `.gpx`, inspect stats, and share routes.
- **Compass**: live device heading with GPS coordinates and elevation.
- **Language**: Indonesian and English UI selectable in Settings.

## Running the App

### Prerequisites

- macOS + Xcode
- OpenJDK 21 (`brew install openjdk@21`)

### Run Tests

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew :composeApp:jvmTest :composeApp:iosSimulatorArm64Test --no-daemon
```

### Build & Run on iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and press Run (`Cmd+R`), or build from the terminal for the simulator:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

If you use **Zed**, all KMP test, compile, and iOS simulator launch tasks are preconfigured in `.zed/tasks.json`.
