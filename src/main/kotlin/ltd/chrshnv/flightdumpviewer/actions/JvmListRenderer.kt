package ltd.chrshnv.flightdumpviewer.actions

import ltd.chrshnv.flightdumpviewer.recording.JvmAttachService
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

class JvmListRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is JvmAttachService.JvmInfo) {
            text = "${value.pid} — ${value.displayName}"
        }
        return c
    }
}
