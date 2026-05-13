package ltd.chrshnv.flightdumpviewer.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.JBUI
import ltd.chrshnv.flightdumpviewer.jfr.JfrLoader
import ltd.chrshnv.flightdumpviewer.jfr.JfrRecording
import ltd.chrshnv.flightdumpviewer.ui.events.EventBrowserPanel
import ltd.chrshnv.flightdumpviewer.ui.flamegraph.FlameGraphPanel
import ltd.chrshnv.flightdumpviewer.ui.gc.GcTimelinePanel
import ltd.chrshnv.flightdumpviewer.ui.hotspots.HotSpotsPanel
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class JfrFileEditor(
    private val project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val rootPanel = JPanel(BorderLayout())
    private val loading = JBLabel("Loading ${file.name}...", SwingConstants.CENTER).apply {
        border = JBUI.Borders.empty(16)
    }

    init {
        rootPanel.add(loading, BorderLayout.CENTER)
        startLoad()
    }

    private fun startLoad() {
        val path = Paths.get(file.path)
        object : Task.Backgroundable(project, "Loading JFR recording ${file.name}", true) {
            override fun run(indicator: ProgressIndicator) {
                val recording = JfrLoader.load(path, indicator)
                SwingUtilities.invokeLater { populate(recording) }
            }

            override fun onThrowable(error: Throwable) {
                SwingUtilities.invokeLater {
                    rootPanel.removeAll()
                    rootPanel.add(
                        JBLabel("Failed to load: ${error.message}", SwingConstants.CENTER),
                        BorderLayout.CENTER,
                    )
                    rootPanel.revalidate()
                    rootPanel.repaint()
                }
            }
        }.queue()
    }

    private fun populate(recording: JfrRecording) {
        rootPanel.removeAll()

        val tabs = JBTabbedPane(SwingConstants.TOP)
        tabs.addTab("Flame Graph", FlameGraphPanel(recording.cpu))
        tabs.addTab("Hot Spots", HotSpotsPanel(recording.cpu))
        tabs.addTab("GC & Memory", GcTimelinePanel(recording))
        tabs.addTab("Events", EventBrowserPanel(recording))

        rootPanel.add(tabs, BorderLayout.CENTER)
        rootPanel.revalidate()
        rootPanel.repaint()
    }

    override fun getComponent(): JComponent = rootPanel
    override fun getPreferredFocusedComponent(): JComponent = rootPanel
    override fun getName(): String = "JFR Viewer"
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun dispose() {
        Disposer.dispose(this)
    }
}
