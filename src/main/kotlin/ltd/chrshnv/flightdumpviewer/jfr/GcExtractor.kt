package ltd.chrshnv.flightdumpviewer.jfr

import ltd.chrshnv.flightdumpviewer.model.GcPause
import ltd.chrshnv.flightdumpviewer.model.GcTimelinePoint
import jdk.jfr.consumer.RecordedEvent

class GcExtractor {
    private val heap = ArrayList<GcTimelinePoint>()
    private val pauses = ArrayList<GcPause>()

    fun isHeapSummary(name: String): Boolean = name == "jdk.GCHeapSummary"
    fun isGcEvent(name: String): Boolean =
        name == "jdk.GarbageCollection" || name == "jdk.YoungGarbageCollection" || name == "jdk.OldGarbageCollection"

    fun accept(event: RecordedEvent) {
        when (event.eventType.name) {
            "jdk.GCHeapSummary" -> heap += extractHeap(event)
            "jdk.GarbageCollection",
            "jdk.YoungGarbageCollection",
            "jdk.OldGarbageCollection" -> pauses += extractPause(event)
        }
    }

    private fun extractHeap(event: RecordedEvent): GcTimelinePoint {
        val used = if (event.hasField("heapUsed")) event.getLong("heapUsed") else 0L
        val committed = if (event.hasField("heapSpace.committedSize"))
            event.getLong("heapSpace.committedSize") else used
        return GcTimelinePoint(event.startTime, used, committed)
    }

    private fun extractPause(event: RecordedEvent): GcPause {
        val cause = if (event.hasField("cause")) event.getString("cause") ?: "?" else "?"
        val name = if (event.hasField("name")) event.getString("name") ?: "?" else event.eventType.name
        return GcPause(event.startTime, event.duration.toNanos(), cause, name)
    }

    fun heapTimeline(): List<GcTimelinePoint> = heap.sortedBy { it.timestamp }
    fun gcPauses(): List<GcPause> = pauses.sortedBy { it.timestamp }
}
