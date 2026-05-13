package ltd.chrshnv.flightdumpviewer.jfr

import jdk.jfr.consumer.RecordedEvent

class AllocationExtractor {
    private val perType = HashMap<String, Stat>()
    var totalBytes: Long = 0
        private set
    var totalAllocations: Long = 0
        private set

    fun isAllocationEvent(name: String): Boolean =
        name == "jdk.ObjectAllocationInNewTLAB" ||
            name == "jdk.ObjectAllocationOutsideTLAB" ||
            name == "jdk.ObjectAllocationSample"

    fun accept(event: RecordedEvent) {
        val typeName = (event.getValue<Any?>("objectClass") as? jdk.jfr.consumer.RecordedClass)
            ?.name ?: return
        val size = when {
            event.hasField("allocationSize") -> event.getLong("allocationSize")
            event.hasField("weight") -> event.getLong("weight")
            event.hasField("tlabSize") -> event.getLong("tlabSize")
            else -> 0L
        }
        totalAllocations++
        totalBytes += size
        val s = perType.getOrPut(typeName) { Stat() }
        s.count++
        s.bytes += size
    }

    fun summary(): List<AllocationSummary> = perType.entries
        .map { AllocationSummary(it.key, it.value.count, it.value.bytes) }
        .sortedByDescending { it.bytes }

    private class Stat {
        var count: Long = 0
        var bytes: Long = 0
    }
}

data class AllocationSummary(val typeName: String, val count: Long, val bytes: Long)
