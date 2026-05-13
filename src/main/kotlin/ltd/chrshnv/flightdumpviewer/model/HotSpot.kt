package ltd.chrshnv.flightdumpviewer.model

class HotSpot(val method: String) {
    var selfSamples: Long = 0
    var totalSamples: Long = 0
    val callers: MutableMap<String, Long> = HashMap()
    val callees: MutableMap<String, Long> = HashMap()
}
