package ltd.chrshnv.flightdumpviewer.ui.hotspots

import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import ltd.chrshnv.flightdumpviewer.jfr.JfrRecording
import ltd.chrshnv.flightdumpviewer.model.HotSpot
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class HotSpotsPanel(view: JfrRecording.CpuView) : JPanel(BorderLayout()) {

    private val hotSpots: List<HotSpot> = view.hotSpots
    private val total: Long = view.totalSamples.coerceAtLeast(1)

    init {
        if (hotSpots.isEmpty()) {
            add(JBLabel("No CPU samples recorded.", SwingConstants.CENTER), BorderLayout.CENTER)
        } else {
            val mainTable = JBTable(HotSpotTableModel(hotSpots, total)).apply {
                setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
                autoCreateRowSorter = true
                installPercentRenderer(this, 1)
                installPercentRenderer(this, 2)
            }

            val callersModel = ContributionTableModel("Caller")
            val calleesModel = ContributionTableModel("Callee")
            val callers = JBTable(callersModel).apply { autoCreateRowSorter = true }
            val callees = JBTable(calleesModel).apply { autoCreateRowSorter = true }

            mainTable.selectionModel.addListSelectionListener {
                if (it.valueIsAdjusting) return@addListSelectionListener
                val row = mainTable.selectedRow
                if (row < 0) {
                    callersModel.set(emptyList()); calleesModel.set(emptyList()); return@addListSelectionListener
                }
                val modelRow = mainTable.convertRowIndexToModel(row)
                val hs = hotSpots[modelRow]
                callersModel.set(hs.callers.entries.sortedByDescending { it.value }.map { it.key to it.value })
                calleesModel.set(hs.callees.entries.sortedByDescending { it.value }.map { it.key to it.value })
            }

            val bottom = JBSplitter(false, 0.5f).apply {
                firstComponent = wrap("Callers", callers)
                secondComponent = wrap("Callees", callees)
            }

            val splitter = JBSplitter(true, 0.6f).apply {
                firstComponent = JBScrollPane(mainTable)
                secondComponent = bottom
            }
            add(splitter, BorderLayout.CENTER)
        }
    }

    private fun wrap(title: String, table: JBTable): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBLabel(title).apply { border = JBUI.Borders.empty(4, 8) }, BorderLayout.NORTH)
        panel.add(JBScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    private fun installPercentRenderer(table: JBTable, columnIndex: Int) {
        table.columnModel.getColumn(columnIndex).cellRenderer = object : DefaultTableCellRenderer() {
            init { horizontalAlignment = SwingConstants.RIGHT }
            override fun setValue(value: Any?) {
                text = if (value is Number) "%.1f%%".format(value.toDouble()) else value?.toString().orEmpty()
            }
        }
    }
}

private class HotSpotTableModel(
    private val rows: List<HotSpot>,
    private val total: Long,
) : AbstractTableModel() {
    private val columns = arrayOf("Method", "Self %", "Total %", "Self samples", "Total samples")
    override fun getRowCount(): Int = rows.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]
    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        0 -> String::class.java
        1, 2 -> java.lang.Double::class.java
        else -> java.lang.Long::class.java
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val hs = rows[rowIndex]
        return when (columnIndex) {
            0 -> hs.method
            1 -> 100.0 * hs.selfSamples / total
            2 -> 100.0 * hs.totalSamples / total
            3 -> hs.selfSamples
            4 -> hs.totalSamples
            else -> ""
        }
    }
}

private class ContributionTableModel(private val labelName: String) : AbstractTableModel() {
    private var data: List<Pair<String, Long>> = emptyList()
    fun set(rows: List<Pair<String, Long>>) {
        data = rows; fireTableDataChanged()
    }
    override fun getRowCount(): Int = data.size
    override fun getColumnCount(): Int = 2
    override fun getColumnName(column: Int): String = if (column == 0) labelName else "Samples"
    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == 0) String::class.java else java.lang.Long::class.java
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val (name, count) = data[rowIndex]
        return if (columnIndex == 0) name else count
    }
}
