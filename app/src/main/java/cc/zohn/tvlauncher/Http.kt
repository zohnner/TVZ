package cc.zohn.tvlauncher

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val USER_AGENT = "ZohnTV/1.0 (personal TV launcher)"
private const val TIMEOUT_MS = 10_000

// Blocking; call from Dispatchers.IO.
fun httpGetJson(url: String): JSONObject = open(url).let { connection ->
    try {
        JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
        connection.disconnect()
    }
}

// Blocking; call from Dispatchers.IO. Writes to a temp file first so a failed download leaves nothing.
fun httpDownload(url: String, target: File) {
    val connection = open(url)
    val partial = File(target.path + ".part")
    try {
        connection.inputStream.use { input -> partial.outputStream().use { input.copyTo(it) } }
        partial.renameTo(target)
    } finally {
        partial.delete()
        connection.disconnect()
    }
}

private fun open(url: String): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("User-Agent", USER_AGENT)
    }
