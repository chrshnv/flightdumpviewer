package ltd.chrshnv.flightdumpviewer.ui.gc

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import ltd.chrshnv.flightdumpviewer.jfr.JfrRecording
import ltd.chrshnv.flightdumpviewer.model.GcPause
import ltd.chrshnv.flightdumpviewer.model.GcTimelinePoint
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class GcTimelinePanel(recording: JfrRecording) : JPanel(BorderLayout()) {

    init {
        if (recording.gc.heap.isEmpty() && recording.gc.pauses.isEmpty()) {
            add(JBLabel("No GC events in this recording.", SwingConstants.CENTER), BorderLayout.CENTER)
        } else {
            val start = recording.firstEventTime?.toEpochMilli() ?: 0L
            val end = recording.lastEventTime?.toEpochMilli() ?: (start + 1L)
            add(Canvas(recording.gc.heap, recording.gc.pauses, start, end), BorderLayout.CENTER)
            add(legend(recording), BorderLayout.SOUTH)
        }
    }

    private fun legend(recording: JfrRecording): JPanel {
        val pauses = recording.gc.pauses
        val maxPause = pauses.maxOfOrNull { it.durationNanos } ?: 0L
        val avgPause = if (pauses.isEmpty()) 0L else pauses.sumOf { it.durationNanos } / pauses.size
        val label = JBLabel(
            "GC pauses: ${pauses.size}   max: ${formatNanos(maxPause)}   avg: ${formatNanos(avgPause)}",
        ).apply { border = JBUI.Borders.empty(4, 8) }
        val panel = JPanel(BorderLayout())
        panel.add(label, BorderLayout.WEST)
        return panel
    }

    private fun formatNanos(ns: Long): String = when {
        ns >= 1_000_000_000 -> "%.2f s".format(ns / 1e9)
        ns >= 1_000_000 -> "%.1f ms".format(ns / 1e6)
        ns >= 1_000 -> "%.1f µs".format(ns / 1e3)
        else -> "$ns ns"
    }

    private class Canvas(
        private val heap: List<GcTimelinePoint>,
        private val pauses: List<GcPause>,
        private val startMs: Long,
        private val endMs: Long,
    ) : JComponent() {

        init {
            preferredSize = Dimension(JBUI.scale(800), JBUI.scale(360))
            isOpaque = true
            background = JBColor.background()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = background
            g2.fillRect(0, 0, width, height)

            val pad = JBUI.scale(28)
            val plotX = pad
            val plotW = width - 2 * pad
            val heapPlotY = pad
            val heapPlotH = (height * 0.55).toInt() - pad
            val pausePlotY = heapPlotY + heapPlotH + JBUI.scale(20)
            val pausePlotH = height - pausePlotY - pad

            if (plotW <= 0 || heapPlotH <= 0 || pausePlotH <= 0) return

            val durationMs = (endMs - startMs).coerceAtLeast(1L)

            // Heap area
            val maxHeap = (heap.maxOfOrNull { it.heapCommittedBytes.coerceAtLeast(it.heapUsedBytes) } ?: 1L)
                .coerceAtLeast(1L)
            g2.color = JBColor.border()
            g2.drawRect(plotX, heapPlotY, plotW, heapPlotH)
            if (heap.isNotEmpty()) {
                val poly = java.awt.Polygon()
                poly.addPoint(plotX, heapPlotY + heapPlotH)
                for (p in heap) {
                    val x = plotX + (((p.timestamp.toEpochMilli() - startMs).toDouble() / durationMs) * plotW).toInt()
                    val y = heapPlotY + heapPlotH -
                        ((p.heapUsedBytes.toDouble() / maxHeap) * heapPlotH).toInt()
                    poly.addPoint(x, y)
                }
                poly.addPoint(plotX + plotW, heapPlotY + heapPlotH)
                g2.color = areaColor()
                g2.fillPolygon(poly)
                g2.color = lineColor()
                g2.stroke = BasicStroke(JBUI.scale(1).toFloat())
                for (i in 1 until heap.size) {
                    val p0 = heap[i - 1]; val p1 = heap[i]
                    val x0 = plotX + (((p0.timestamp.toEpochMilli() - startMs).toDouble() / durationMs) * plotW).toInt()
                    val y0 = heapPlotY + heapPlotH - ((p0.heapUsedBytes.toDouble() / maxHeap) * heapPlotH).toInt()
                    val x1 = plotX + (((p1.timestamp.toEpochMilli() - startMs).toDouble() / durationMs) * plotW).toInt()
                    val y1 = heapPlotY + heapPlotH - ((p1.heapUsedBytes.toDouble() / maxHeap) * heapPlotH).toInt()
                    g2.drawLine(x0, y0, x1, y1)
                }
            }

            g2.color = JBColor.foreground()
            g2.drawString("Heap used", plotX + 4, heapPlotY + 12)
            g2.drawString(formatBytes(maxHeap), plotX + plotW - 80, heapPlotY + 12)

            // Pauses
            g2.color = JBColor.border()
            g2.drawRect(plotX, pausePlotY, plotW, pausePlotH)
            val maxPause = (pauses.maxOfOrNull { it.durationNanos } ?: 1L).coerceAtLeast(1L)
            for (p in pauses) {
                val x = plotX + (((p.timestamp.toEpochMilli() - startMs).toDouble() / durationMs) * plotW).toInt()
                val h = ((p.durationNanos.toDouble() / maxPause) * pausePlotH).toInt().coerceAtLeast(1)
                g2.color = pauseColor()
                g2.fillRect(x - 1, pausePlotY + pausePlotH - h, JBUI.scale(2), h)
            }
            g2.color = JBColor.foreground()
            g2.drawString("GC pauses", plotX + 4, pausePlotY + 12)
        }

        private fun areaColor(): Color =
            JBColor(Color(0x4a90e2).withAlpha(70), Color(0x4a90e2).withAlpha(90))
        private fun lineColor(): Color = JBColor(Color(0x2c6dbf), Color(0x6aa8e8))
        private fun pauseColor(): Color = JBColor(Color(0xc0392b), Color(0xe67e73))

        private fun formatBytes(b: Long): String = when {
            b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
            b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
            b >= 1L shl 10 -> "%.1f KB".format(b / (1L shl 10).toDouble())
            else -> "$b B"
        }

        private fun Color.withAlpha(a: Int): Color = Color(red, green, blue, a)
    }
}
