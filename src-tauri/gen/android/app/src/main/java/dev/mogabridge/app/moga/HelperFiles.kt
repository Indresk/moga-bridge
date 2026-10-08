package dev.mogabridge.app.moga

import android.content.Context
import dev.mogabridge.app.R
import java.io.File
import java.io.IOException
import java.security.SecureRandom

/**
 * Files shared with the adb-started virtual-gamepad helper.
 *
 * They live in the app's *external* private directory
 * (`Android/data/<package>/files`): the adb `shell` user can write there and the app can
 * read it, while other apps cannot browse it on Android 11+ (scoped storage).
 *
 *  - `helper.token`: random secret the helper demands as the first line of every connection.
 *  - `helper.sh`: generated script that starts/stops the helper, so an end user only needs
 *    `adb` and one command, not this repository.
 */
internal class HelperFiles(private val context: Context) {
    private fun directory(): File {
        return context.getExternalFilesDir(null)
            ?: throw IOException("External storage is not available.")
    }

    /** Create the token (once) and refresh the script. Safe to call repeatedly. */
    fun prepare() {
        val dir = directory()
        val token = File(dir, TOKEN_FILE)
        if (!token.exists() || token.length() == 0L) {
            token.writeText(newToken())
        }
        val template = context.resources.openRawResource(R.raw.helper_script)
            .bufferedReader()
            .use { it.readText() }
        val script = template
            .replace("@DIR@", shellPath(dir))
            .replace("@PORT@", UinputBridge.DEFAULT_PORT.toString())
        val scriptFile = File(dir, SCRIPT_FILE)
        // Status polling calls this often; avoid rewriting an unchanged file.
        if (!scriptFile.exists() || scriptFile.readText() != script) {
            scriptFile.writeText(script)
        }
    }

    fun readToken(): String? {
        return try {
            File(directory(), TOKEN_FILE).readText().trim().ifEmpty { null }
        } catch (_: IOException) {
            null
        }
    }

    /** The exact command a user runs from a PC to start the helper. */
    fun startCommand(): String = "adb shell sh ${shellPath(directory())}/$SCRIPT_FILE"

    /** Same location as seen from the adb shell (`/sdcard` is the primary user's alias). */
    private fun shellPath(dir: File): String =
        dir.absolutePath.replaceFirst("/storage/emulated/0", "/sdcard")

    private fun newToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TOKEN_FILE = "helper.token"
        private const val SCRIPT_FILE = "helper.sh"
    }
}
