package ltd.chrshnv.flightdumpviewer.model

import java.time.Instant

data class GcTimelinePoint(
    val timestamp: Instant,
    val heapUsedBytes: Long,
    val heapCommittedBytes: Long,
)

data class GcPause(
    val timestamp: Instant,
    val durationNanos: Long,
    val cause: String,
    val name: String,
)
