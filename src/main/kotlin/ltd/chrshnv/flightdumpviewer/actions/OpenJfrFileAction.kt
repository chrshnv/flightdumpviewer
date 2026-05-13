package ltd.chrshnv.flightdumpviewer.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import ltd.chrshnv.flightdumpviewer.JfrFileType

class OpenJfrFileAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.fileType === JfrFileType || it.extension.equals("jfr", true) }
            .withTitle("Open JFR Recording")
        FileChooser.chooseFile(descriptor, project, null) { vf ->
            FileEditorManager.getInstance(project).openFile(vf, true, true)
        }
    }
}
