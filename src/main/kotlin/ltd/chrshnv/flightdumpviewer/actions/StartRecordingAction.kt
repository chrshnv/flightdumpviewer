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

class StartRecordingAction : AnAction() {

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
            .setTitle("Start JFR Recording — Select JVM")
            .setRenderer(JvmListRenderer())
            .setItemChosenCallback { selected -> promptDurationAndStart(project, service, selected) }
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    private fun promptDurationAndStart(
        project: Project,
        service: JvmAttachService,
        selected: JvmAttachService.JvmInfo,
    ) {
        val settings = JfrSettings.get().state
        val durationInput = Messages.showInputDialog(
            project,
            "Duration in seconds (blank for unbounded):",
            "JFR Recording",
            null,
            settings.defaultDurationSeconds.toString(),
            null,
        ) ?: return
        val durationSeconds = durationInput.trim().toIntOrNull() ?: 0

        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        val outDir = settings.outputDirectory.ifBlank { System.getProperty("java.io.tmpdir") ?: "." }
        val file: Path = Paths.get(outDir, "fdv-${selected.pid}-$timestamp.jfr")
        val recordingName = "fdv-$timestamp"

        object : Task.Backgroundable(project, "Starting JFR recording on ${selected.displayName}", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val response = service.startRecording(
                    pid = selected.pid,
                    file = file,
                    durationSeconds = durationSeconds,
                    settingsProfile = settings.defaultSettingsProfile,
                    name = recordingName,
                )
                notify(project, "Started recording on PID ${selected.pid}: $response", NotificationType.INFORMATION)

                if (durationSeconds > 0 && settings.autoOpenRecordings) {
                    val deadline = System.currentTimeMillis() + durationSeconds * 1000L + 2_000L
                    while (System.currentTimeMillis() < deadline) {
                        indicator.checkCanceled()
                        Thread.sleep(500)
                    }
                    openWhenReady(project, file)
                }
            }

            override fun onThrowable(error: Throwable) {
                notify(
                    project,
                    "Failed to start JFR recording on PID ${selected.pid}: " +
                        (error.message ?: error.javaClass.simpleName),
                    NotificationType.ERROR,
                )
            }
        }.queue()
    }

    private fun openWhenReady(project: Project, path: Path) {
        ApplicationManager.getApplication().invokeLater {
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: return@invokeLater
            FileEditorManager.getInstance(project).openFile(vf, true, true)
        }
    }

    private fun notify(project: Project, msg: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("JFR Viewer")
            .createNotification(msg, type)
            .notify(project)
    }
}
