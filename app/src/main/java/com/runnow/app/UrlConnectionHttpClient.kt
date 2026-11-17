package com.runnow.app.data

import com.runnow.data.HttpClient
import com.runnow.data.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Klien HTTP GET dengan HttpURLConnection (tanpa dependensi tambahan). Jaringan gagal = IOException,
 * yang diterjemahkan IcuHttp/OpenMeteoHttp. BELUM DIUJI di perangkat.
 */
class UrlConnectionHttpClient : HttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.requestMethod = "GET"
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                for ((k, v) in headers) c.setRequestProperty(k, v)
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                HttpResponse(code, body)
            } finally {
                c.disconnect()
            }
        }
}
