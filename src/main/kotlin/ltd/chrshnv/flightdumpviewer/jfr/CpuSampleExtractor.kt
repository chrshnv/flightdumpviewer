package ltd.chrshnv.flightdumpviewer.jfr

import ltd.chrshnv.flightdumpviewer.model.HotSpot
import ltd.chrshnv.flightdumpviewer.model.StackTreeNode
import jdk.jfr.consumer.RecordedEvent
import jdk.jfr.consumer.RecordedFrame
import jdk.jfr.consumer.RecordedMethod

class CpuSampleExtractor {
    val root: StackTreeNode = StackTreeNode.newRoot()
    var totalSamples: Long = 0
        private set

    fun isSampleEvent(name: String): Boolean =
        name == "jdk.ExecutionSample" || name == "jdk.NativeMethodSample"

    fun accept(event: RecordedEvent) {
        val st = event.stackTrace ?: return
        val frames = st.frames ?: return
        if (frames.isEmpty()) return
        totalSamples++

        var node = root
        node.totalSamples++
        for (i in frames.indices.reversed()) {
            val label = formatFrame(frames[i])
            node = node.childFor(label)
            node.totalSamples++
        }
        node.selfSamples++
    }

    fun buildHotSpots(): List<HotSpot> {
        val byMethod = HashMap<String, HotSpot>()
        walk(root, null, byMethod)
        return byMethod.values.sortedByDescending { it.totalSamples }
    }

    private fun walk(node: StackTreeNode, parentMethod: String?, out: MutableMap<String, HotSpot>) {
        if (node.frame != StackTreeNode.ROOT_FRAME) {
            val hs = out.getOrPut(node.frame) { HotSpot(node.frame) }
            hs.selfSamples += node.selfSamples
            hs.totalSamples += node.totalSamples
            if (parentMethod != null) {
                hs.callers.merge(parentMethod, node.totalSamples, Long::plus)
                out.getOrPut(parentMethod) { HotSpot(parentMethod) }
                    .callees.merge(node.frame, node.totalSamples, Long::plus)
            }
        }
        val nextParent = if (node.frame == StackTreeNode.ROOT_FRAME) null else node.frame
        for (child in node.children.values) walk(child, nextParent, out)
    }

    companion object {
        fun formatFrame(frame: RecordedFrame): String {
            val method = frame.method ?: return "<unknown>"
            return formatMethod(method)
        }

        fun formatMethod(method: RecordedMethod): String {
            val type = method.type?.name ?: "?"
            val name = method.name ?: "?"
            return "$type.$name"
        }
    }
}
