package ltd.chrshnv.flightdumpviewer.recording

import com.sun.tools.attach.VirtualMachine
import com.sun.tools.attach.VirtualMachineDescriptor
import java.lang.reflect.Method
import java.nio.file.Path

/**
 * Thin wrapper around `com.sun.tools.attach.VirtualMachine` and HotSpot's diagnostic command channel.
 *
 * The `executeJCmd` call is invoked reflectively because it lives in `sun.tools.attach.HotSpotVirtualMachine`
 * which is not part of the supported API surface. The JBR exposes this class, so reflective access works.
 */
class JvmAttachService {

    data class JvmInfo(val pid: String, val displayName: String)

    fun listJvms(): List<JvmInfo> = VirtualMachine.list().map(::describe)

    private fun describe(d: VirtualMachineDescriptor): JvmInfo {
        val name = d.displayName().takeIf { it.isNotBlank() } ?: "pid ${d.id()}"
        return JvmInfo(d.id(), name)
    }

    fun startRecording(pid: String, file: Path, durationSeconds: Int, settingsProfile: String, name: String): String {
        val args = buildString {
            append("name=").append(name)
            append(" filename=").append(file.toAbsolutePath())
            if (durationSeconds > 0) append(" duration=").append(durationSeconds).append('s')
            append(" settings=").append(settingsProfile)
        }
        return runJCmd(pid, "JFR.start $args")
    }

    fun stopRecording(pid: String, name: String, file: Path?): String {
        val args = buildString {
            append("name=").append(name)
            if (file != null) append(" filename=").append(file.toAbsolutePath())
        }
        return runJCmd(pid, "JFR.stop $args")
    }

    fun dumpRecording(pid: String, name: String, file: Path): String =
        runJCmd(pid, "JFR.dump name=$name filename=${file.toAbsolutePath()}")

    fun checkRecordings(pid: String): String = runJCmd(pid, "JFR.check")

    private fun runJCmd(pid: String, command: String): String {
        val vm = VirtualMachine.attach(pid)
        try {
            val executeJCmd: Method = vm.javaClass.getMethod("executeJCmd", String::class.java)
            executeJCmd.isAccessible = true
            val input = executeJCmd.invoke(vm, command) as java.io.InputStream
            return input.use { it.readBytes() }.toString(Charsets.UTF_8)
        } finally {
            vm.detach()
        }
    }
}
