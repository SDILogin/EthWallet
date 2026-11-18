package me.sdi.ethwallet.core.network

import android.net.TrafficStats
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

private const val HTTP_SOCKET_STATS_TAG = 0xE7

/**
 * Creates sockets that get a traffic stats tag when they connect.
 * StrictMode reports an untagged socket as a violation.
 */
class StatsTaggedSocketFactory : SocketFactory() {
    override fun createSocket(): Socket = StatsTaggedSocket()

    override fun createSocket(host: String, port: Int): Socket = createSocket().apply {
        connect(InetSocketAddress(host, port))
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress(host, port))
        }

    override fun createSocket(host: InetAddress, port: Int): Socket = createSocket().apply {
        connect(InetSocketAddress(host, port))
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localAddress, localPort))
            connect(InetSocketAddress(address, port))
        }
}

private class StatsTaggedSocket : Socket() {
    override fun connect(endpoint: SocketAddress, timeout: Int) {
        val previousTag = TrafficStats.getThreadStatsTag()
        TrafficStats.setThreadStatsTag(HTTP_SOCKET_STATS_TAG)
        try {
            super.connect(endpoint, timeout)
        } finally {
            TrafficStats.setThreadStatsTag(previousTag)
        }
    }
}
