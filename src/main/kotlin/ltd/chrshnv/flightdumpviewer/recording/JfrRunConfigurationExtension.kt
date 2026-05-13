package ltd.chrshnv.flightdumpviewer.recording

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import ltd.chrshnv.flightdumpviewer.settings.JfrSettings
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Adds `-XX:StartFlightRecording=...` to a run configuration when enabled and opens the resulting
 * file when the JVM exits.
 *
 * Enablement is opt-in per run configuration via a boolean attribute persisted under
 * "ltd.chrshnv.flightdumpviewer.runconfig.enabled" — there is no UI tab yet; set via XML or external action.
 */
class JfrRunConfigurationExtension : RunConfigurationExtension() {

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true

    override fun isEnabledFor(
        applicableConfiguration: RunConfigurationBase<*>,
        runnerSettings: RunnerSettings?,
    ): Boolean = ENABLED_KEY.getBoolean(applicableConfiguration)

    override fun readExternal(runConfiguration: RunConfigurationBase<*>, element: Element) {
        val attr = element.getAttributeValue(ENABLED_KEY.name)
        ENABLED_KEY.set(runConfiguration, attr?.toBoolean() ?: false)
    }

    @Throws(ExecutionException::class)
    override fun <T : RunConfigurationBase<*>> updateJavaParameters(
        configuration: T,
        params: JavaParameters,
        runnerSettings: RunnerSettings?,
    ) {
        if (!ENABLED_KEY.getBoolean(configuration)) return
        val file = nextOutputFile()
        PENDING_FILE.set(configuration, file.toString())

        val s = JfrSettings.get().state
        val durPart = if (s.defaultDurationSeconds > 0) ",duration=${s.defaultDurationSeconds}s" else ""
        params.vmParametersList.add(
            "-XX:StartFlightRecording=" +
                "name=flightdumpviewer-run,filename=$file,settings=${s.defaultSettingsProfile}$durPart",
        )
    }

    override fun attachToProcess(
        configuration: RunConfigurationBase<*>,
        handler: ProcessHandler,
        runnerSettings: RunnerSettings?,
    ) {
        if (!ENABLED_KEY.getBoolean(configuration)) return
        val pendingPath = PENDING_FILE.get(configuration) ?: return
        val project: Project = configuration.project
        val file = Paths.get(pendingPath)
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                if (!JfrSettings.get().state.autoOpenRecordings) return
                if (!Files.exists(file)) {
                    notify(project, "JFR recording was not produced at $file", NotificationType.WARNING)
                    return
                }
                ApplicationManager.getApplication().invokeLater {
                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: return@invokeLater
                    FileEditorManager.getInstance(project).openFile(vf, true, true)
                }
            }
        })
    }

    private fun nextOutputFile(): Path {
        val s = JfrSettings.get().state
        val outDir = s.outputDirectory.ifBlank { System.getProperty("java.io.tmpdir") ?: "." }
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
        return Paths.get(outDir, "flightdumpviewer-run-$timestamp.jfr")
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("JFR Viewer")
            .createNotification(message, type)
            .notify(project)
    }

    private object ENABLED_KEY {
        const val name = "ltd.chrshnv.flightdumpviewer.runconfig.enabled"
        private val key = Key.create<Boolean>(name)
        fun set(config: RunConfigurationBase<*>, value: Boolean) = config.putCopyableUserData(key, value)
        fun getBoolean(config: RunConfigurationBase<*>): Boolean = config.getCopyableUserData(key) == true
    }

    private object PENDING_FILE {
        private val key = Key.create<String>("ltd.chrshnv.flightdumpviewer.runconfig.pendingFile")
        fun set(config: RunConfigurationBase<*>, value: String) = config.putCopyableUserData(key, value)
        fun get(config: RunConfigurationBase<*>): String? = config.getCopyableUserData(key)
    }
}
