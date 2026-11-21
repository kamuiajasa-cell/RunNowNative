package com.runnow.data

import java.io.IOException
import java.util.Base64

/** Intervals.icu lewat [HttpClient]: Basic auth "API_KEY:<kunci>", seperti icuGet() di HTML. */
class IcuHttp(private val http: HttpClient, private val apiKey: () -> String?) : IcuApi {
    companion object {
        const val BASE = "https://intervals.icu/api/v1/"
    }

    override suspend fun get(path: String): JsonValue {
        val key = apiKey()
        if (key.isNullOrEmpty()) throw IcuException(IcuException.Kind.AUTH, 0, "API key ditolak")
        val auth = "Basic " + Base64.getEncoder().encodeToString("API_KEY:$key".toByteArray(Charsets.UTF_8))
        val res = try {
            http.get(BASE + path.removePrefix("/"), mapOf("Authorization" to auth, "Accept" to "application/json"))
        } catch (e: IOException) {
            throw IcuException(IcuException.Kind.NETWORK, 0, e.message ?: "network error")
        }
        if (res.status == 401 || res.status == 403) throw IcuException(IcuException.Kind.AUTH, res.status, "API key ditolak")
        if (res.status !in 200..299) throw IcuException(IcuException.Kind.HTTP, res.status, "HTTP " + res.status)
        return try {
            Json.parse(res.body)
        } catch (e: JsonException) {
            throw IcuException(IcuException.Kind.HTTP, res.status, "Respons bukan JSON")
        }
    }
}
