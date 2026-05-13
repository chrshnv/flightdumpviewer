package ltd.chrshnv.flightdumpviewer.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import ltd.chrshnv.flightdumpviewer.recording.JvmAttachService
import ltd.chrshnv.flightdumpviewer.settings.JfrSettings
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class StopRecordingAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = JvmAttachService()
        val jvms = try { service.listJvms() } catch (t: Throwable) {
            notify(project, "Could not list local JVMs: ${t.message}", NotificationType.ERROR); return
        }
        if (jvms.isEmpty()) {
            notify(project, "No local JVMs found.", NotificationType.WARNING); return
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(jvms)
            .setTitle("Stop JFR Recording — Select JVM")
            .setRenderer(JvmListRenderer())
            .setItemChosenCallback { selected -> promptNameAndStop(project, service, selected) }
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    private fun promptNameAndStop(
        project: Project,
        service: JvmAttachService,
        selected: JvmAttachService.JvmInfo,
    ) {
        val name = Messages.showInputDialog(
            project,
            "Recording name (use the same name passed to JFR.start):",
            "Stop JFR Recording",
            null,
            "",
            null,
        ) ?: return

        val settings = JfrSettings.get().state
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        val outDir = settings.outputDirectory.ifBlank { System.getProperty("java.io.tmpdir") ?: "." }
        val file: Path = Paths.get(outDir, "fdv-${selected.pid}-$timestamp.jfr")

        object : Task.Backgroundable(project, "Stopping JFR recording on ${selected.displayName}", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val response = service.stopRecording(selected.pid, name, file)
                notify(project, "Stopped recording: $response", NotificationType.INFORMATION)
                if (settings.autoOpenRecordings) {
                    ApplicationManager.getApplication().invokeLater {
                        val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: return@invokeLater
                        FileEditorManager.getInstance(project).openFile(vf, true, true)
                    }
                }
            }

            override fun onThrowable(error: Throwable) {
                notify(
                    project,
                    "Failed to stop JFR recording on PID ${selected.pid}: " +
                        (error.message ?: error.javaClass.simpleName),
                    NotificationType.ERROR,
                )
            }
        }.queue()
    }

    private fun notify(project: Project, msg: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("JFR Viewer")
            .createNotification(msg, type)
            .notify(project)
    }
}
