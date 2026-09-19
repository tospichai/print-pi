package com.maepranam.networkprinter.job

import com.google.gson.Gson
import java.net.URI
import java.util.Locale

sealed interface PrintRequest {
    data class Receipt(val source: URI) : PrintRequest

    data object TestPage : PrintRequest
}

class PrintJobParser(
    private val gson: Gson,
) {
    fun parse(json: String): Result<PrintRequest.Receipt> = try {
        val fullPath = gson.fromJson(json, Payload::class.java)?.fullPath?.trim().orEmpty()
        require(fullPath.isNotEmpty()) { "fullPath is required" }

        val source = URI(fullPath)
        val scheme = source.scheme?.lowercase(Locale.US)
        require(scheme == "http" || scheme == "https") { "fullPath must use HTTP or HTTPS" }
        require(!source.host.isNullOrBlank()) { "fullPath must include a host" }

        Result.success(PrintRequest.Receipt(source))
    } catch (failure: Exception) {
        Result.failure(IllegalArgumentException("Invalid print payload", failure))
    }

    private data class Payload(
        val fullPath: String? = null,
    )
}
