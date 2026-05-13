package ltd.chrshnv.flightdumpviewer.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import ltd.chrshnv.flightdumpviewer.JfrFileType

class JfrFileEditorProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.fileType === JfrFileType ||
            file.extension.equals("jfr", ignoreCase = true)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        JfrFileEditor(project, file)

    override fun getEditorTypeId(): String = "jfr-viewer-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
