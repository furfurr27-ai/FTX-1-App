package dev.n0png.fieldops.android.logbook

import dev.n0png.fieldops.core.logbook.*
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Direct ARRL LoTW transport. Call from a worker/background dispatcher, never the UI thread. */
class LotwHttpTransport : LotwTransport {
    override fun uploadTq8(payload: ByteArray, filename: String): LotwUploadResponse {
        val boundary = "----FieldOpsLoTW${System.nanoTime()}"
        val conn = URI(UPLOAD_URL).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 45_000
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        val out = ByteArrayOutputStream()
        fun s(v: String) = out.write(v.toByteArray(StandardCharsets.UTF_8))
        s("--$boundary\r\nContent-Disposition: form-data; name=\"upfile\"; filename=\"$filename\"\r\n")
        s("Content-Type: application/octet-stream\r\n\r\n")
        out.write(payload)
        s("\r\n--$boundary--\r\n")
        conn.outputStream.use { it.write(out.toByteArray()) }
        val code = conn.responseCode
        val body = (if (code in 200..399) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val result = Regex("<!--\\s*\\.UPL\\.\\s*(accepted|rejected)\\s*-->", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)
        val msg = Regex("<!--\\s*\\.UPLMESSAGE\\.\\s*([\\s\\S]*?)-->", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.trim()
            ?: body.take(1000)
        return LotwUploadResponse(result.equals("accepted", true), msg, code)
    }

    override fun query(credentials: LotwCredentials, params: Map<String, String>): String {
        fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
        val all = linkedMapOf("login" to credentials.username, "password" to credentials.webPassword).apply { putAll(params) }
        val query = all.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val conn = URI("$REPORT_URL?$query").toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 20_000
        conn.readTimeout = 45_000
        conn.setRequestProperty("Accept", "text/plain, text/html;q=0.8")
        val code = conn.responseCode
        val body = (if (code in 200..399) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("LoTW report HTTP $code")
        if (!body.contains("<EOH>", ignoreCase = true)) error("LoTW authentication/report error")
        return body
    }

    companion object {
        const val UPLOAD_URL = "https://lotw.arrl.org/lotw/upload"
        const val REPORT_URL = "https://lotw.arrl.org/lotwuser/lotwreport.adi"
    }
}
