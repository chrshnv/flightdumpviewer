package ltd.chrshnv.flightdumpviewer.jfr

import ltd.chrshnv.flightdumpviewer.model.EventRow
import ltd.chrshnv.flightdumpviewer.model.EventTypeSummary
import ltd.chrshnv.flightdumpviewer.model.GcPause
import ltd.chrshnv.flightdumpviewer.model.GcTimelinePoint
import ltd.chrshnv.flightdumpviewer.model.HotSpot
import ltd.chrshnv.flightdumpviewer.model.StackTreeNode
import java.nio.file.Path
import java.time.Instant

class JfrRecording(
    val path: Path,
    val firstEventTime: Instant?,
    val lastEventTime: Instant?,
    val totalEvents: Long,
    val cpu: CpuView,
    val gc: GcView,
    val allocations: List<AllocationSummary>,
    private val eventTypes: List<EventTypeSummary>,
    private val rowsByType: (String) -> List<EventRow>,
) {
    val durationMillis: Long
        get() {
            val a = firstEventTime ?: return 0L
            val b = lastEventTime ?: return 0L
            return b.toEpochMilli() - a.toEpochMilli()
        }

    fun eventTypes(): List<EventTypeSummary> = eventTypes
    fun rows(eventType: String): List<EventRow> = rowsByType(eventType)

    class CpuView(
        val root: StackTreeNode,
        val totalSamples: Long,
        val hotSpots: List<HotSpot>,
    )

    class GcView(
        val heap: List<GcTimelinePoint>,
        val pauses: List<GcPause>,
    )
}
