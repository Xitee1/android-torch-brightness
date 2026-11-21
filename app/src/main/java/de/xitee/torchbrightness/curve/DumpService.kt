package de.xitee.torchbrightness.curve

import kotlin.system.exitProcess

/** Runs inside the Shizuku process (shell uid), which holds the DUMP permission. */
class DumpService : IDumpService.Stub() {

    override fun dumpDisplay(): String = DumpParser.relevantLines(runDumpsysDisplay())

    override fun destroy() {
        exitProcess(0)
    }
}

/** Runs `dumpsys display` with the permissions of the calling process. */
fun runDumpsysDisplay(): String {
    val process = ProcessBuilder("dumpsys", "display").redirectErrorStream(true).start()
    return process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
}
