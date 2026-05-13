package ltd.chrshnv.flightdumpviewer.ui.events

import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import ltd.chrshnv.flightdumpviewer.jfr.JfrRecording
import ltd.chrshnv.flightdumpviewer.model.EventRow
import ltd.chrshnv.flightdumpviewer.model.EventTypeSummary
import java.awt.BorderLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import java.awt.Component
import java.time.format.DateTimeFormatter
import java.time.ZoneId

class EventBrowserPanel(recording: JfrRecording) : JPanel(BorderLayout()) {
    init {
        val types = recording.eventTypes()
        val typeList = JBList(types).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = TypeRenderer()
        }

        val rowsModel = EventRowsTableModel()
        val rowsTable = JBTable(rowsModel).apply {
            autoCreateRowSorter = true
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        }
        val detail = JBTextArea().apply {
            isEditable = false
            font = JBUI.Fonts.create("Monospaced", 12)
            border = JBUI.Borders.empty(6)
        }

        typeList.addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            val type = typeList.selectedValue ?: return@addListSelectionListener
            rowsModel.set(type, recording.rows(type.name))
            detail.text = ""
        }

        rowsTable.selectionModel.addListSelectionListener {
            if (it.valueIsAdjusting) return@addListSelectionListener
            val row = rowsTable.selectedRow
            if (row < 0) {
                detail.text = ""; return@addListSelectionListener
            }
            val modelRow = rowsTable.convertRowIndexToModel(row)
            detail.text = formatDetail(rowsModel.rowAt(modelRow))
        }

        val rowsAndDetail = JBSplitter(true, 0.6f).apply {
            firstComponent = JBScrollPane(rowsTable)
            secondComponent = JBScrollPane(detail)
        }
        val outer = JBSplitter(false, 0.25f).apply {
            firstComponent = JBScrollPane(typeList)
            secondComponent = rowsAndDetail
        }
        add(outer, BorderLayout.CENTER)

        if (types.isNotEmpty()) typeList.selectedIndex = 0
    }

    private fun formatDetail(row: EventRow): String {
        val sb = StringBuilder()
        sb.append("Event: ").append(row.eventType).append('\n')
        sb.append("Time:  ").append(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(row.timestamp.atZone(ZoneId.systemDefault())),
        ).append('\n')
        if (row.durationNanos > 0) sb.append("Duration: ").append(row.durationNanos).append(" ns\n")
        sb.append('\n')
        for ((k, v) in row.fields) sb.append(k).append(" = ").append(v).append('\n')
        if (row.stackTrace.isNotEmpty()) {
            sb.append("\nStack:\n")
            for (frame in row.stackTrace) sb.append("  at ").append(frame).append('\n')
        }
        return sb.toString()
    }
}

private class TypeRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
    ): Component {
        val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        if (value is EventTypeSummary) {
            text = "${value.label ?: value.name}  (${value.count})"
        }
        return c
    }
}

private class EventRowsTableModel : AbstractTableModel() {
    private var columns: List<String> = emptyList()
    private var rows: List<EventRow> = emptyList()

    fun set(type: EventTypeSummary, newRows: List<EventRow>) {
        columns = listOf("Time") + type.fieldNames.filterNot { it.equals("startTime", ignoreCase = true) }
        rows = newRows
        fireTableStructureChanged()
    }

    fun rowAt(index: Int): EventRow = rows[index]

    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val r = rows[rowIndex]
        return if (columnIndex == 0) {
            DateTimeFormatter.ISO_LOCAL_TIME.format(r.timestamp.atZone(ZoneId.systemDefault()))
        } else {
            r.fields[columns[columnIndex]] ?: ""
        }
    }
}

@Suppress("unused")
private val styleHint = SimpleTextAttributes.GRAY_ATTRIBUTES
