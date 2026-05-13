package ltd.chrshnv.flightdumpviewer.ui.flamegraph

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import ltd.chrshnv.flightdumpviewer.jfr.JfrRecording
import ltd.chrshnv.flightdumpviewer.model.StackTreeNode
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.max
import kotlin.math.min

class FlameGraphPanel(private val view: JfrRecording.CpuView) : JPanel(BorderLayout()) {

    init {
        if (view.totalSamples == 0L) {
            add(JBLabel("No CPU samples recorded.", SwingConstants.CENTER), BorderLayout.CENTER)
        } else {
            val canvas = Canvas(view.root)

            val actions = DefaultActionGroup().apply {
                add(BackAction(canvas))
                add(ResetAction(canvas))
                addSeparator()
                add(ZoomInAction(canvas))
                add(ZoomOutAction(canvas))
            }
            val toolbar: ActionToolbar = ActionManager.getInstance()
                .createActionToolbar("FlameGraphToolbar", actions, true)
            toolbar.targetComponent = canvas

            val toolbarHost = JPanel(BorderLayout()).apply {
                add(toolbar.component, BorderLayout.WEST)
                val hint = JBLabel(
                    " Click frame: focus subtree · Right-click / Esc: back · Wheel: zoom · Drag: pan",
                ).apply { border = JBUI.Borders.emptyLeft(8) }
                add(hint, BorderLayout.CENTER)
                border = JBUI.Borders.customLineBottom(JBColor.border())
            }

            add(toolbarHost, BorderLayout.NORTH)
            add(canvas, BorderLayout.CENTER)
            add(
                JBLabel("${view.totalSamples} CPU samples").apply { border = JBUI.Borders.empty(4, 8) },
                BorderLayout.SOUTH,
            )
        }
    }

    /**
     * Canvas with two independent zoom mechanisms:
     *  - **Subtree focus** (re-root): clicking a frame pushes the previous root onto a history stack
     *    and re-roots the tree at the clicked frame. Right-click or Esc pops one level.
     *  - **Viewport zoom/pan**: viewScale ≥ 1, viewOffset is the left edge as a fraction of
     *    the displayRoot's width [0, 1 - 1/viewScale]. Mouse wheel scales around the cursor.
     */
    class Canvas(initialRoot: StackTreeNode) : JComponent() {
        private val rootNode: StackTreeNode = initialRoot
        private val zoomStack: ArrayDeque<StackTreeNode> = ArrayDeque()
        private var displayRoot: StackTreeNode = initialRoot
        private val rowHeight = JBUI.scale(18)
        private var hovered: StackTreeNode? = null

        private var viewScale: Double = 1.0       // 1.0 = whole displayRoot fills width
        private var viewOffset: Double = 0.0      // [0, 1 - 1/viewScale]

        private var dragOriginX: Int = -1
        private var dragOriginOffset: Double = 0.0
        private var dragMoved: Boolean = false

        init {
            preferredSize = Dimension(JBUI.scale(800), JBUI.scale(400))
            isOpaque = true
            background = JBColor.background()
            toolTipText = ""
            isFocusable = true

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    requestFocusInWindow()
                    if (e.button == MouseEvent.BUTTON1) {
                        dragOriginX = e.x
                        dragOriginOffset = viewOffset
                        dragMoved = false
                        cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    cursor = Cursor.getDefaultCursor()
                    val wasDrag = dragMoved
                    dragOriginX = -1
                    if (wasDrag) return
                    when (e.button) {
                        MouseEvent.BUTTON1 -> {
                            val hit = hitTest(e.x, e.y) ?: return
                            focusOn(hit)
                        }
                        MouseEvent.BUTTON3 -> back()
                    }
                }

                override fun mouseExited(e: MouseEvent) {
                    if (hovered != null) {
                        hovered = null; repaint()
                    }
                }
            })

            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val prev = hovered
                    hovered = hitTest(e.x, e.y)
                    if (prev !== hovered) repaint()
                }

                override fun mouseDragged(e: MouseEvent) {
                    if (dragOriginX < 0) return
                    val dxPx = e.x - dragOriginX
                    if (!dragMoved && kotlin.math.abs(dxPx) < JBUI.scale(3)) return
                    dragMoved = true
                    val visibleFrac = 1.0 / viewScale
                    val deltaFrac = -dxPx.toDouble() / width * visibleFrac
                    viewOffset = clampOffset(dragOriginOffset + deltaFrac)
                    repaint()
                }
            })

            addMouseWheelListener { e ->
                val factor = if (e.preciseWheelRotation < 0) 1.25 else 1.0 / 1.25
                zoomAround(e.x, factor)
                e.consume()
            }

            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    when (e.keyCode) {
                        KeyEvent.VK_ESCAPE, KeyEvent.VK_BACK_SPACE -> back()
                        KeyEvent.VK_HOME -> resetAll()
                        KeyEvent.VK_PLUS, KeyEvent.VK_EQUALS -> zoomAround(width / 2, 1.25)
                        KeyEvent.VK_MINUS -> zoomAround(width / 2, 1.0 / 1.25)
                    }
                }
            })
        }

        fun canGoBack(): Boolean = zoomStack.isNotEmpty() || viewScale != 1.0 || viewOffset != 0.0

        fun back() {
            if (viewScale != 1.0 || viewOffset != 0.0) {
                viewScale = 1.0
                viewOffset = 0.0
                repaint()
                return
            }
            val prev = zoomStack.removeLastOrNull() ?: return
            displayRoot = prev
            repaint()
        }

        fun resetAll() {
            zoomStack.clear()
            displayRoot = rootNode
            viewScale = 1.0
            viewOffset = 0.0
            repaint()
        }

        fun zoomAround(anchorX: Int, factor: Double) {
            val w = width
            if (w <= 0) return
            val anchorFracInView = (anchorX.toDouble() / w).coerceIn(0.0, 1.0)
            val anchorFrac = viewOffset + anchorFracInView / viewScale
            val newScale = (viewScale * factor).coerceIn(1.0, 10000.0)
            if (newScale == viewScale) return
            viewScale = newScale
            viewOffset = clampOffset(anchorFrac - anchorFracInView / viewScale)
            repaint()
        }

        private fun focusOn(node: StackTreeNode) {
            if (node === displayRoot) return
            zoomStack.addLast(displayRoot)
            displayRoot = node
            viewScale = 1.0
            viewOffset = 0.0
            repaint()
        }

        private fun clampOffset(v: Double): Double {
            val maxOffset = max(0.0, 1.0 - 1.0 / viewScale)
            return v.coerceIn(0.0, maxOffset)
        }

        override fun getToolTipText(event: MouseEvent?): String? {
            val ev = event ?: return null
            val hit = hitTest(ev.x, ev.y) ?: return null
            val total = displayRoot.totalSamples.coerceAtLeast(1)
            val pct = 100.0 * hit.totalSamples / total
            return "<html>${hit.frame}<br>${hit.totalSamples} samples " +
                "(self ${hit.selfSamples}) — ${"%.1f".format(pct)}%</html>"
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = background
            g2.fillRect(0, 0, width, height)

            val total = displayRoot.totalSamples.toDouble().coerceAtLeast(1.0)
            // Map fraction f in displayRoot → screen x: (f - viewOffset) * viewScale * width
            val rootScreenX = -viewOffset * viewScale * width
            val rootScreenW = viewScale * width
            paintNode(g2, displayRoot, rootScreenX, rootScreenW, height - rowHeight, total)
        }

        private fun paintNode(
            g2: Graphics2D,
            node: StackTreeNode,
            x: Double,
            widthD: Double,
            y: Int,
            total: Double,
        ) {
            if (widthD < 0.5) return
            if (x + widthD < 0 || x > width) {
                // Off-screen at this level — children inherit the same x extent and won't be visible either.
                return
            }
            val visibleX = max(0, x.toInt())
            val visibleW = min(width, (x + widthD).toInt()) - visibleX
            if (visibleW <= 0) return

            val isHover = hovered === node
            val base = colorFor(node.frame)
            g2.color = if (isHover) base.brighter() else base
            g2.fillRect(visibleX, y, visibleW, rowHeight - 1)
            g2.color = JBColor.border()
            g2.drawRect(visibleX, y, visibleW, rowHeight - 1)

            if (visibleW > JBUI.scale(40)) {
                g2.color = JBColor.foreground()
                val fm = g2.fontMetrics
                val drawn = clip(fm, node.frame, visibleW - JBUI.scale(6))
                g2.drawString(drawn, visibleX + JBUI.scale(3), y + fm.ascent + 1)
            }

            val childY = y - rowHeight
            if (childY < 0) return
            var cursorX = x
            val children = node.children.values.sortedByDescending { it.totalSamples }
            for (child in children) {
                val cw = widthD * (child.totalSamples.toDouble() / total)
                paintNode(g2, child, cursorX, cw, childY, total)
                cursorX += cw
            }
        }

        private fun hitTest(px: Int, py: Int): StackTreeNode? {
            val total = displayRoot.totalSamples.toDouble().coerceAtLeast(1.0)
            val rootScreenX = -viewOffset * viewScale * width
            val rootScreenW = viewScale * width
            return walk(displayRoot, rootScreenX, rootScreenW, height - rowHeight, total, px, py)
        }

        private fun walk(
            node: StackTreeNode,
            x: Double,
            w: Double,
            y: Int,
            total: Double,
            px: Int,
            py: Int,
        ): StackTreeNode? {
            if (px < x || px > x + w) return null
            if (py in y..(y + rowHeight - 1)) return node
            val childY = y - rowHeight
            if (childY < 0) return null
            var cursor = x
            val children = node.children.values.sortedByDescending { it.totalSamples }
            for (child in children) {
                val cw = w * (child.totalSamples.toDouble() / total)
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
            return Color(Color.HSBtoRGB(h, sat, brt))
        }
    }

    private class BackAction(private val canvas: Canvas) :
        AnAction("Back", "Zoom out one step (Esc)", AllIcons.Actions.Back) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) { e.presentation.isEnabled = canvas.canGoBack() }
        override fun actionPerformed(e: AnActionEvent) = canvas.back()
    }

    private class ResetAction(private val canvas: Canvas) :
        AnAction("Reset Zoom", "Reset to full tree (Home)", AllIcons.Actions.Rerun) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) { e.presentation.isEnabled = canvas.canGoBack() }
        override fun actionPerformed(e: AnActionEvent) = canvas.resetAll()
    }

    private class ZoomInAction(private val canvas: Canvas) :
        AnAction("Zoom In", "Zoom in horizontally (+)", AllIcons.General.ZoomIn) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun actionPerformed(e: AnActionEvent) = canvas.zoomAround(canvas.width / 2, 1.25)
    }

    private class ZoomOutAction(private val canvas: Canvas) :
        AnAction("Zoom Out", "Zoom out horizontally (-)", AllIcons.General.ZoomOut) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun actionPerformed(e: AnActionEvent) = canvas.zoomAround(canvas.width / 2, 1.0 / 1.25)
    }
}
