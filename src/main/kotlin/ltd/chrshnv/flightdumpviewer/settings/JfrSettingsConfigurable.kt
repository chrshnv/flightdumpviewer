package ltd.chrshnv.flightdumpviewer.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel

class JfrSettingsConfigurable : Configurable {

    private val durationField = JBTextField(6)
    private val profileField = JBTextField(16)
    private val outputDirField = TextFieldWithBrowseButton()
    private val autoOpenCheckbox = JBCheckBox("Auto-open recordings after they finish")

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "JFR Viewer"

    override fun createComponent(): JComponent {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Output Directory")
            .withDescription("JFR recordings will be written here")
        outputDirField.addBrowseFolderListener(null, descriptor)

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Default recording duration (seconds):"), durationField, 1, false)
            .addLabeledComponent(JBLabel("JFR settings profile (e.g. 'default' or 'profile'):"), profileField, 1, false)
            .addLabeledComponent(JBLabel("Output directory:"), outputDirField, 1, false)
            .addComponent(autoOpenCheckbox)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        form.border = JBUI.Borders.empty(12)
        panel = form
        reset()
        return form
    }

    override fun isModified(): Boolean {
        val s = JfrSettings.get().state
        return durationField.text.trim() != s.defaultDurationSeconds.toString() ||
            profileField.text.trim() != s.defaultSettingsProfile ||
            outputDirField.text.trim() != s.outputDirectory ||
            autoOpenCheckbox.isSelected != s.autoOpenRecordings
    }

    override fun apply() {
        val s = JfrSettings.get().state
        s.defaultDurationSeconds = durationField.text.trim().toIntOrNull()?.coerceAtLeast(1) ?: 60
        s.defaultSettingsProfile = profileField.text.trim().ifEmpty { "profile" }
        s.outputDirectory = outputDirField.text.trim()
        s.autoOpenRecordings = autoOpenCheckbox.isSelected
    }

    override fun reset() {
        val s = JfrSettings.get().state
        durationField.text = s.defaultDurationSeconds.toString()
        profileField.text = s.defaultSettingsProfile
        outputDirField.text = s.outputDirectory
        autoOpenCheckbox.isSelected = s.autoOpenRecordings
    }

    override fun disposeUIResources() {
        panel = null
    }
}
