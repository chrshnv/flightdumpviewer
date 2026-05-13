package ltd.chrshnv.flightdumpviewer.jfr

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import jdk.jfr.consumer.RecordingFile
import java.nio.file.Path
import java.time.Instant

object JfrLoader {

    /**
     * Loads a JFR file in a single pass and returns a fully populated [JfrRecording].
     * Honors cancellation on the supplied [indicator] (may be null).
     */
    fun load(path: Path, indicator: ProgressIndicator? = null): JfrRecording {
        val cpu = CpuSampleExtractor()
        val gc = GcExtractor()
        val alloc = AllocationExtractor()
        val index = EventTypeIndex()

        var firstTs: Instant? = null
        var lastTs: Instant? = null
        var total = 0L

        RecordingFile(path).use { rf ->
            indicator?.isIndeterminate = true
            indicator?.text = "Reading ${path.fileName}"

            while (rf.hasMoreEvents()) {
                if (total and 0x3FFL == 0L) {
                    if (indicator?.isCanceled == true) throw ProcessCanceledException()
                }
                val event = rf.readEvent()
                total++

                val ts = event.startTime
                if (firstTs == null || ts.isBefore(firstTs)) firstTs = ts
                if (lastTs == null || ts.isAfter(lastTs)) lastTs = ts

                val name = event.eventType.name
                when {
                    cpu.isSampleEvent(name) -> cpu.accept(event)
                    gc.isHeapSummary(name) || gc.isGcEvent(name) -> gc.accept(event)
                    alloc.isAllocationEvent(name) -> alloc.accept(event)
                }
                index.accept(event)

                if (total and 0xFFFFL == 0L) {
                    indicator?.text2 = "$total events"
                }
            }
        }

        // Propagate self/total samples up: child total counts already incremented during accept,
        // but root's totalSamples reflects all samples — accurate for percent calculations.

        return JfrRecording(
            path = path,
            firstEventTime = firstTs,
            lastEventTime = lastTs,
            totalEvents = total,
            cpu = JfrRecording.CpuView(cpu.root, cpu.totalSamples, cpu.buildHotSpots()),
            gc = JfrRecording.GcView(gc.heapTimeline(), gc.gcPauses()),
            allocations = alloc.summary(),
            eventTypes = index.types(),
            rowsByType = { index.rowsOf(it) },
        )
    }
}
