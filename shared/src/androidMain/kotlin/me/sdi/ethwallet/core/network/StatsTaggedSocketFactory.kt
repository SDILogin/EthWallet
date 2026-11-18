package me.sdi.ethwallet.core.network

import android.net.TrafficStats
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

private const val HTTP_SOCKET_STATS_TAG = 0xE7

/**
 * Creates sockets with a traffic stats tag. StrictMode reports an untagged socket as a violation.
 *
 * Android tags a socket when it creates the socket file descriptor. A new `Socket` creates the file descriptor on the
 * first call that needs it. The no-argument `createSocket()` calls `setSoTimeout` while the thread tag is set, so the
 * file descriptor gets the tag.
 */
class StatsTaggedSocketFactory : SocketFactory() {
    override fun createSocket(): Socket {
        val previousTag = TrafficStats.getThreadStatsTag()
        TrafficStats.setThreadStatsTag(HTTP_SOCKET_STATS_TAG)
        try {
            return Socket().apply { soTimeout = 0 }
        } finally {
            TrafficStats.setThreadStatsTag(previousTag)
        }
    }

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
