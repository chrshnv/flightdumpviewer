package ltd.chrshnv.flightdumpviewer.model

class StackTreeNode(val frame: String) {
    val children: MutableMap<String, StackTreeNode> = LinkedHashMap()
    var selfSamples: Long = 0
    var totalSamples: Long = 0

    fun childFor(frame: String): StackTreeNode =
        children.getOrPut(frame) { StackTreeNode(frame) }

    companion object {
        const val ROOT_FRAME: String = "<root>"
        fun newRoot(): StackTreeNode = StackTreeNode(ROOT_FRAME)
    }
}
