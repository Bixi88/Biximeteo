package app.meteopersonale.data

import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Http {
    class Result(
        val code: Int,
        val body: String?,
        val expiresEpoch: Long?,
        val lastModified: String?
    )

    fun get(url: String, userAgent: String, ifModifiedSince: String? = null): Result {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", userAgent)
            if (!ifModifiedSince.isNullOrBlank()) {
                c.setRequestProperty("If-Modified-Since", ifModifiedSince)
            }
            val code = c.responseCode
            val body = if (code in 200..299) {
                c.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
            val expires = c.getHeaderField("Expires")?.let {
                runCatching {
                    ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
                }.getOrNull()
            }
            return Result(code, body, expires, c.getHeaderField("Last-Modified"))
        } finally {
            c.disconnect()
        }
    }
}
