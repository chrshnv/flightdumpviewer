package ltd.chrshnv.flightdumpviewer.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "FlightDumpViewerSettings", storages = [Storage("flightdumpviewer.xml")])
class JfrSettings : PersistentStateComponent<JfrSettings.State> {
    data class State(
        var defaultDurationSeconds: Int = 60,
        var defaultSettingsProfile: String = "profile", // jdk profile template
        var outputDirectory: String = System.getProperty("java.io.tmpdir") ?: "",
        var autoOpenRecordings: Boolean = true,
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        fun get(): JfrSettings = ApplicationManager.getApplication().getService(JfrSettings::class.java)
    }
}
