# Flight Dump Viewer

A JetBrains IDE plugin that opens and visualizes Java Flight Recorder (`.jfr`) recordings directly inside the editor — no need to fire up JDK Mission Control.

## Features

- **CPU flame graph** — width-proportional, click-to-zoom, theme-aware
- **Hot Spots table** — sortable methods with self/total CPU %, plus a callers/callees drill-down split
- **GC & memory timeline** — heap-used area + GC-pause bars over recording wall-clock
- **Generic event browser** — pick any JFR event type, inspect raw fields and stack traces
- **Open existing recordings** — `Tools → Open JFR Recording…`
- **Attach to a running JVM** — `Tools → JFR Recording → Start Recording on Local JVM…` (uses `com.sun.tools.attach` + HotSpot's `JFR.start` diagnostic command)
- **Record from a run configuration** — injects `-XX:StartFlightRecording=…` and auto-opens the produced file
- Settings under **Preferences → Tools → JFR Viewer** (default duration, settings profile, output dir, auto-open)

Parsing is done with the JDK's built-in `jdk.jfr.consumer.RecordingFile` — no extra runtime dependencies.

## Compatibility

| | Version |
| --- | --- |
| Target IDE | IntelliJ IDEA Community 2024.2+ (Community-compatible, works in Ultimate / other IDEA-family IDEs) |
| Build runtime | JDK 21 |
| Language | Kotlin 1.9 |
| Build | IntelliJ Platform Gradle Plugin v2 |

## Build

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew buildPlugin
```

The plugin zip lands at `build/distributions/flightdumpviewer-0.1.0.zip`. Install it via **Settings → Plugins → ⚙ → Install Plugin from Disk…**.

## Run a sandbox IDE

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew runIde
```

## Run tests

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

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

## Status

v0.1.0 — early. Open .jfr files, attach to JVMs, and run-config recording all work. Roadmap:

- per-run-config UI tab to toggle "record JFR on run"
- thread filter and time-range filter on Hot Spots
- flame graph ↔ hot-spots cross-selection
- checked-in sample `.jfr` for integration tests

## License

Apache License 2.0 — see [LICENSE](LICENSE).
