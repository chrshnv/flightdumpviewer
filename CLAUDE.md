# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build, test, run

JDK 21 is required. Newer JDKs (25+) break Gradle 8.10 (`IllegalArgumentException: 25`) and the IntelliJ Platform Gradle Plugin v2 expects JDK 21. Newer IDE platform versions (2024.2+) also won't run on JDK 17. Always export `JAVA_HOME` first:

```sh
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.10-librca   # or any JDK 21
export PATH=$JAVA_HOME/bin:$PATH
```

| Task | Command |
| --- | --- |
| Compile | `./gradlew compileKotlin` |
| Tests | `./gradlew test` |
| Single test | `./gradlew test --tests 'ltd.chrshnv.flightdumpviewer.jfr.CpuSampleExtractorTest'` |
| Build plugin zip | `./gradlew buildPlugin` → `build/distributions/flightdumpviewer-<ver>.zip` |
| Run sandbox IDE | `./gradlew runIde` (windowed; opens IntelliJ Community 2024.2.4 with the plugin installed) |
| Plugin Verifier | `./gradlew verifyPlugin` (slow — downloads target IDE distributions) |

`runIde` restages the plugin from source on each launch, so changes only land after closing the sandbox window and re-running. The first `runIde` downloads ~1 GB of IDE binaries into `~/.gradle/caches`.

## Architecture

Three layers, single Gradle module under `src/main/kotlin/ltd/chrshnv/flightdumpviewer/`:

```
parsing  (jfr/)        jdk.jfr.consumer.RecordingFile → typed events
   ↓
model    (model/)      StackTreeNode, HotSpot, GcTimelinePoint, EventRow — immutable, computed once
   ↓
ui       (ui/)         FileEditor with 4 panels + ToolWindow
```

**Parsing is single-pass.** `JfrLoader.load(path)` opens the recording, iterates `hasMoreEvents()/readEvent()` once, and dispatches each `RecordedEvent` to four extractors by event-type name. The whole `JfrRecording` (CPU stack tree + hot spots + GC timeline + per-type event index) is built in that one walk and never re-parsed. UI panels receive the recording and only render — they never touch the file again.

Loading runs inside a `Task.Backgroundable` with cancellation polled every 1 K events. The editor (`JfrFileEditor`) shows a "Loading…" label until the parse finishes, then swaps in a `JBTabbedPane` with the four panels.

**Stack tree → hot spots derivation.** `CpuSampleExtractor` builds the per-frame `StackTreeNode` tree directly from `event.stackTrace.frames` (reversed: outermost frame becomes a child of root). After the parse, one post-order walk over that same tree produces the `HotSpot` list with self/total counts plus caller/callee maps — no second event pass. If you add new analyses derived from CPU samples, add them to `buildHotSpots`-style post-order walks rather than re-iterating events.

**Event browser is the only lazy-ish path.** `EventTypeIndex` keeps up to `DEFAULT_MAX_ROWS_PER_TYPE` (50 000) materialised rows per event type. Above that cap, only the count is tracked. If you change this, watch heap pressure on multi-GB recordings.

**Flame graph viewport.** `FlameGraphPanel.Canvas` has two independent zoom mechanisms:
- **Subtree focus** (`displayRoot` + `zoomStack`): clicking re-roots the tree; right-click / Esc / Backspace pops one step.
- **Viewport zoom/pan** (`viewScale ≥ 1`, `viewOffset` ∈ [0, 1−1/scale]): mouse wheel scales around the cursor; drag pans. `back()` unwinds the viewport before unwinding the subtree stack.

Painting clips off-screen frames, so deep zooms stay fast.

## IntelliJ Platform conventions to know

- **`FileEditorPolicy.HIDE_DEFAULT_EDITOR` requires `DumbAware`.** `JfrFileEditorProvider` implements both. Don't drop `DumbAware` without changing the policy, or the platform refuses to register the editor at plugin load.
- **JFR file type uses `JfrFileType : FileType` (not `LanguageFileType`)** — `.jfr` is binary, has no language. Don't add a `Language`.
- **`<depends>com.intellij.modules.java</depends>`** in `plugin.xml` is required for `JfrRunConfigurationExtension`. Removing it crashes the run-config extension at startup.
- **Run-config integration is opt-in via copyable user data** keyed `ltd.chrshnv.flightdumpviewer.runconfig.enabled`. There is no UI tab yet — the flag must be set externally (or via the run-config XML). Adding a `SettingsEditor` is on the roadmap.
- **JVM attach uses reflection on `executeJCmd`** (`JvmAttachService`). That method lives in `sun.tools.attach.HotSpotVirtualMachine` and isn't part of the supported API; the JBR exposes it. If a future JBR locks it down, fall back to `loadAgent` + JMX.
- **Action update threads:** all custom `AnAction` subclasses set `ActionUpdateThread.BGT` (or EDT for the flame-graph toolbar buttons). Don't omit `getActionUpdateThread()` — the platform deprecation warns and may break in 2025.x.

## CI

- `.github/workflows/build.yml` — runs on push/PR. Builds, tests, uploads zip as workflow artifact. On push to `main` only, also publishes the same zip to a moving `nightly` pre-release at `releases/download/nightly/flightdumpviewer-nightly.zip`.
- `.github/workflows/release.yml` — runs on `v*` tag push. Builds with `-PpluginVersion=<tag>` (overrides `gradle.properties`) and creates a GitHub Release with auto-generated notes plus the zip.

To cut a real release: `git tag v0.X.Y && git push origin v0.X.Y`.

## Plugin compatibility range

`pluginSinceBuild=252`, `pluginUntilBuild=253.*`. We build against IC 2025.2.4 (252.x), with Kotlin 2.2.0 (required because the platform's bundled `util-8.jar` ships Kotlin stdlib metadata 2.2.0 — anything older fails compile with "actual metadata version is 2.2.0 but compiler reads up to 2.0.0"). When bumping `untilBuild`, also bump `platformVersion` periodically to catch real API breaks earlier (and run `verifyPlugin`).

The test runtime needs `junit:junit:4.13.2` even though our tests are JUnit 5 — the platform's JUnit5 environment initializer touches `org.junit.rules.TestRule` via `Logger.setFactory` and throws `NoClassDefFoundError` otherwise.

## Gotchas observed in this repo

- Setting `pluginUntilBuild` too tight excludes 2025.x IDEs (we hit this — 243.* excluded the user's 253 build).
- `gradle/wrapper/gradle-wrapper.properties` was bumped from the 7.5.1 wrapper we copied from another project to 8.10.2; if you copy this scaffold elsewhere, update both the URL and consider regenerating the wrapper jar with `gradle wrapper --gradle-version 8.10.2` once a system Gradle is available.
- `org.gradle.configuration-cache=true` is on. If a task is incompatible (rare, but happens with some platform plugin tasks across upgrades), disable per-invocation with `--no-configuration-cache` rather than removing it from `gradle.properties`.
