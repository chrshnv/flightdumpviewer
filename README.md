# Flight Dump Viewer

A JetBrains IDE plugin that opens and visualizes Java Flight Recorder (`.jfr`) recordings directly inside the editor — no need to fire up JDK Mission Control.

## Features

- **CPU flame graph** — width-proportional, theme-aware, with mouse-wheel zoom around the cursor, drag-to-pan, click-to-focus subtree, and a back/reset toolbar
- **Hot Spots table** — sortable methods with self/total CPU %, plus a callers/callees drill-down split
- **GC & memory timeline** — heap-used area + GC-pause bars over the recording's wall-clock span
- **Generic event browser** — pick any JFR event type, inspect raw fields and stack traces
- **Open existing recordings** — `Tools → Open JFR Recording…`
- **Attach to a running JVM** — `Tools → JFR Recording → Start Recording on Local JVM…` (uses `com.sun.tools.attach` + HotSpot's `JFR.start` diagnostic command)
- **Record from a run configuration** — injects `-XX:StartFlightRecording=…` and auto-opens the produced file
- Settings under **Preferences → Tools → JFR Viewer** (default duration, settings profile, output dir, auto-open)

Parsing is done with the JDK's built-in `jdk.jfr.consumer.RecordingFile` — no extra runtime dependencies.

## Install

Grab the latest stable build:

- **Latest release** — [v1.0.0](https://github.com/chrshnv/flightdumpviewer/releases/latest)
- **Nightly (auto-built from `main`)** — [`flightdumpviewer-nightly.zip`](https://github.com/chrshnv/flightdumpviewer/releases/download/nightly/flightdumpviewer-nightly.zip)

In the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and pick the zip.

> ⚠️ Don't download from the Actions tab — `actions/upload-artifact@v4` re-wraps
> uploads in an extra zip, so the file you'd get is a zip *containing* the plugin
> zip. The Release URLs above serve the real plugin zip.

## Compatibility

| | Version |
| --- | --- |
| Target IDE | IntelliJ IDEA Community / Ultimate **2025.2+** (build 252) |
| Build runtime | JDK 21 |
| Language | Kotlin 2.2 |
| Build | IntelliJ Platform Gradle Plugin v2.6 |

The plugin requires the bundled Java module, so it loads in IDEA but not in non-Java JetBrains IDEs (PyCharm, GoLand, etc.).

## Build

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew buildPlugin
```

The plugin zip lands at `build/distributions/flightdumpviewer-<version>.zip`.

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew runIde     # sandbox IDE
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test       # run tests
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew verifyPlugin
```

JDK 21 is required — the IntelliJ Platform 2025.x doesn't run on 17, and Gradle 8.10 doesn't support JDK 25+.

For deeper architecture and contributor notes, see [CLAUDE.md](CLAUDE.md).

## Releasing

A `v*` tag triggers `.github/workflows/release.yml`:

1. Builds the plugin with the tag's version (`-PpluginVersion=…`).
2. Creates a GitHub Release with auto-generated notes and the zip attached.

```sh
git tag v1.1.0
git push origin v1.1.0
```

### Nightly builds

Every push to `main` produces a nightly via `.github/workflows/build.yml`,
published to a moving `nightly` pre-release at the URL listed in [Install](#install).

## Project layout

```
src/main/kotlin/ltd/chrshnv/flightdumpviewer/
├─ jfr/        # JfrLoader + extractors (CPU, allocations, GC, event type index)
├─ model/      # In-memory analyses: StackTreeNode, HotSpot, GcTimelinePoint, EventRow
├─ ui/         # FileEditor, 4 view panels (flamegraph / hotspots / gc / events), tool window
├─ actions/    # Open .jfr, Start / Stop recording on local JVM
├─ recording/  # JvmAttachService, JfrRunConfigurationExtension
└─ settings/   # Persistent state + Configurable
```

## Roadmap

- per-run-config UI tab to toggle "record JFR on run" (currently opt-in via XML attribute)
- thread filter and time-range filter on Hot Spots
- flame graph ↔ hot-spots cross-selection
- checked-in sample `.jfr` for integration tests

## License

Apache License 2.0 — see [LICENSE](LICENSE).
