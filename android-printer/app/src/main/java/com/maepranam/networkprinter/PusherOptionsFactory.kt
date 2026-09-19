package com.maepranam.networkprinter

import com.pusher.client.PusherOptions

object PusherOptionsFactory {
    fun create(cluster: String): PusherOptions = PusherOptions()
        .setCluster(cluster)
        .setUseTLS(true)
        .setMaxReconnectionAttempts(Int.MAX_VALUE)
        .setMaxReconnectGapInSeconds(MAX_RECONNECT_GAP_SECONDS)

    private const val MAX_RECONNECT_GAP_SECONDS = 30
}
