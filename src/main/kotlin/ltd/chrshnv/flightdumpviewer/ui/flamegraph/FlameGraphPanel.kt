package ltd.chrshnv.flightdumpviewer.ui.flamegraph

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import ltd.chrshnv.flightdumpviewer.jfr.JfrRecording
import ltd.chrshnv.flightdumpviewer.model.StackTreeNode
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

class FlameGraphPanel(private val view: JfrRecording.CpuView) : JPanel(BorderLayout()) {

    init {
        if (view.totalSamples == 0L) {
            add(JBLabel("No CPU samples recorded.", SwingConstants.CENTER), BorderLayout.CENTER)
        } else {
            add(Canvas(view.root), BorderLayout.CENTER)
            val footer = JBLabel("${view.totalSamples} CPU samples").apply {
                border = JBUI.Borders.empty(4, 8)
            }
            add(footer, BorderLayout.SOUTH)
        }
    }

    private class Canvas(initialRoot: StackTreeNode) : JComponent() {
        private var displayRoot: StackTreeNode = initialRoot
        private val rowHeight = JBUI.scale(18)
        private var hovered: HitFrame? = null

        init {
            preferredSize = Dimension(JBUI.scale(800), JBUI.scale(400))
            isOpaque = true
            background = JBColor.background()
            toolTipText = ""

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val hit = hitTest(e.x, e.y) ?: return
                    displayRoot = if (e.clickCount >= 2 && hit.node !== displayRoot) hit.node else hit.node
                    revalidate(); repaint()
                }
            })
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val prev = hovered
                    hovered = hitTest(e.x, e.y)
                    if (prev != hovered) repaint()
                }
            })
        }

        override fun getToolTipText(event: MouseEvent?): String? {
            val hit = hitTest(event?.x ?: return null, event.y) ?: return null
            val total = displayRoot.totalSamples.coerceAtLeast(1)
            val pct = 100.0 * hit.node.totalSamples / total
            return "<html>${hit.node.frame}<br>${hit.node.totalSamples} samples " +
                "(self ${hit.node.selfSamples}) — ${"%.1f".format(pct)}%</html>"
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = background
            g2.fillRect(0, 0, width, height)

            val total = displayRoot.totalSamples.toDouble().coerceAtLeast(1.0)
            paintNode(g2, displayRoot, 0, width.toDouble(), height - rowHeight, total, 0)
        }

        private fun paintNode(
            g2: Graphics2D,
            node: StackTreeNode,
            x: Int,
            widthD: Double,
            y: Int,
            total: Double,
            depth: Int,
        ) {
            if (widthD < 1.0) return
            val w = widthD.toInt().coerceAtLeast(1)
            val rect = Rectangle(x, y, w, rowHeight - 1)
            val isHover = hovered?.node === node
            val base = colorFor(node.frame)
            g2.color = if (isHover) base.brighter() else base
            g2.fillRect(rect.x, rect.y, rect.width, rect.height)
            g2.color = JBColor.border()
            g2.drawRect(rect.x, rect.y, rect.width, rect.height)

            if (w > JBUI.scale(40)) {
                g2.color = JBColor.foreground()
                val text = node.frame
                val fm = g2.fontMetrics
                val drawn = clip(fm, text, w - JBUI.scale(6))
                g2.drawString(drawn, rect.x + JBUI.scale(3), rect.y + fm.ascent + 1)
            }

            // Lay out children in deterministic order
            var cursorX = x.toDouble()
            val childY = y - rowHeight
            if (childY < 0) return
            val children = node.children.values.sortedByDescending { it.totalSamples }
            for (child in children) {
                val cw = widthD * (child.totalSamples.toDouble() / total.coerceAtLeast(1.0))
                paintNode(g2, child, cursorX.toInt(), cw, childY, total, depth + 1)
                cursorX += cw
            }
        }

        private fun hitTest(px: Int, py: Int): HitFrame? {
            val total = displayRoot.totalSamples.toDouble().coerceAtLeast(1.0)
            return walk(displayRoot, 0.0, width.toDouble(), height - rowHeight, total, px, py)
        }

        private fun walk(
            node: StackTreeNode,
            x: Double,
            w: Double,
            y: Int,
            total: Double,
            px: Int,
            py: Int,
        ): HitFrame? {
            if (px < x || px > x + w) return null
            if (py in y..(y + rowHeight - 1)) return HitFrame(node)
            val childY = y - rowHeight
            if (childY < 0) return null
            var cursor = x
            val children = node.children.values.sortedByDescending { it.totalSamples }
            for (child in children) {
                val cw = w * (child.totalSamples.toDouble() / total.coerceAtLeast(1.0))
                val hit = walk(child, cursor, cw, childY, total, px, py)
                if (hit != null) return hit
                cursor += cw
            }
            return null
        }

        private fun clip(fm: java.awt.FontMetrics, text: String, maxWidth: Int): String {
            if (fm.stringWidth(text) <= maxWidth) return text
            val ellipsis = "…"
            var end = text.length
            while (end > 0 && fm.stringWidth(text.substring(0, end) + ellipsis) > maxWidth) end--
            return if (end <= 0) "" else text.substring(0, end) + ellipsis
        }

        private fun colorFor(frame: String): Color {
            val pkg = frame.substringBeforeLast('.', "")
            val h = (pkg.hashCode() and 0xffff) / 65535f
            val sat = 0.45f
            val brt = if (JBColor.isBright()) 0.92f else 0.55f
            val rgb = Color.HSBtoRGB(h, sat, brt)
            return Color(rgb)
        }
    }

    private data class HitFrame(val node: StackTreeNode)
}
