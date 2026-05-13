package ltd.chrshnv.flightdumpviewer.model

import java.time.Instant

data class EventRow(
    val eventType: String,
    val timestamp: Instant,
    val durationNanos: Long,
    val fields: Map<String, String>,
    val stackTrace: List<String>,
)

data class EventTypeSummary(
    val name: String,
    val label: String?,
    val category: List<String>,
    val fieldNames: List<String>,
    var count: Long,
)
