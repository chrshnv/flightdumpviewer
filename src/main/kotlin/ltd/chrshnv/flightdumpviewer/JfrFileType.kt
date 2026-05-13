package ltd.chrshnv.flightdumpviewer

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

object JfrFileType : FileType {
    override fun getName(): String = "JFR"
    override fun getDescription(): String = "Java Flight Recorder recording"
    override fun getDefaultExtension(): String = "jfr"
    override fun getIcon(): Icon = AllIcons.FileTypes.Any_type
    override fun isBinary(): Boolean = true
    override fun isReadOnly(): Boolean = true
    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}
