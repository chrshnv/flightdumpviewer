package ltd.chrshnv.flightdumpviewer.jfr

import ltd.chrshnv.flightdumpviewer.model.StackTreeNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CpuSampleExtractorTest {

    @Test
    fun emptyExtractorHasEmptyRoot() {
        val e = CpuSampleExtractor()
        assertEquals(0L, e.totalSamples)
        assertEquals(StackTreeNode.ROOT_FRAME, e.root.frame)
        assertTrue(e.root.children.isEmpty())
    }

    @Test
    fun sampleEventNameDetection() {
        val e = CpuSampleExtractor()
        assertTrue(e.isSampleEvent("jdk.ExecutionSample"))
        assertTrue(e.isSampleEvent("jdk.NativeMethodSample"))
        assertEquals(false, e.isSampleEvent("jdk.GarbageCollection"))
    }
}
