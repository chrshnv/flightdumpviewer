package ltd.chrshnv.flightdumpviewer.jfr

import ltd.chrshnv.flightdumpviewer.model.EventRow
import ltd.chrshnv.flightdumpviewer.model.EventTypeSummary
import jdk.jfr.consumer.RecordedClass
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordedMethod
import jdk.jfr.consumer.RecordedStackTrace
import jdk.jfr.consumer.RecordedThread

class EventTypeIndex(private val maxRowsPerType: Int = DEFAULT_MAX_ROWS_PER_TYPE) {
    private val summaries = LinkedHashMap<String, EventTypeSummary>()
    private val rows = HashMap<String, MutableList<EventRow>>()

    fun accept(event: RecordedEvent) {
        val type = event.eventType
        val name = type.name
        val summary = summaries.getOrPut(name) {
            EventTypeSummary(
                name = name,
                label = type.label,
                category = type.categoryNames ?: emptyList(),
                fieldNames = type.fields.map { it.name },
                count = 0,
            )
        }
        summary.count++

        val bucket = rows.getOrPut(name) { ArrayList() }
        if (bucket.size < maxRowsPerType) {
            bucket += toRow(event, summary.fieldNames)
        }
    }

    fun types(): List<EventTypeSummary> = summaries.values.sortedByDescending { it.count }
    fun rowsOf(name: String): List<EventRow> = rows[name] ?: emptyList()

    private fun toRow(event: RecordedEvent, fields: List<String>): EventRow {
        val map = LinkedHashMap<String, String>()
        for (f in fields) {
            map[f] = formatValue(event.getValue<Any?>(f))
        }
        return EventRow(
            eventType = event.eventType.name,
            timestamp = event.startTime,
            durationNanos = event.duration.toNanos(),
            fields = map,
            stackTrace = formatStack(event.stackTrace),
        )
    }

    private fun formatValue(value: Any?): String = when (value) {
        null -> ""
        is RecordedClass -> value.name
        is RecordedMethod -> CpuSampleExtractor.formatMethod(value)
        is RecordedThread -> value.javaName ?: value.osName ?: "thread"
        is RecordedStackTrace -> "<stack: ${value.frames?.size ?: 0} frames>"
        is Array<*> -> value.joinToString(", ") { formatValue(it) }
        else -> value.toString()
    }

    private fun formatStack(st: RecordedStackTrace?): List<String> {
        if (st == null) return emptyList()
        val frames = st.frames ?: return emptyList()
        return frames.map { CpuSampleExtractor.formatFrame(it) }
    }

    companion object {
        const val DEFAULT_MAX_ROWS_PER_TYPE: Int = 50_000
    }
}
