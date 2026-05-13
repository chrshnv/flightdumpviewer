package ltd.chrshnv.flightdumpviewer.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import javax.swing.JPanel
import java.awt.BorderLayout

class JfrToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())
        panel.add(
            JBLabel(
                "<html><h3>JFR Viewer</h3>" +
                    "Open a <code>.jfr</code> file from disk, attach to a running JVM via " +
                    "<i>Tools → JFR Recording → Start Recording on Local JVM…</i>, " +
                    "or enable JFR on a run configuration.</html>",
            ).apply { border = JBUI.Borders.empty(12) },
            BorderLayout.NORTH,
        )
        val content = toolWindow.contentManager.factory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
