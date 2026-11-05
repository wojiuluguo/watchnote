package com.ioo.watchnote

import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * 极简局域网下载服务：手机连同一个 Wi-Fi，浏览器打开地址就直接下载文件。
 * 不返回网页，只回一个带 Content-Disposition: attachment 的文件响应，省内存。
 */
internal class ShareServer(private val file: File, private val mime: String) {

    private var server: ServerSocket? = null
    @Volatile
    private var running = false

    @Volatile
    var port: Int = 0
        private set

    /** 从 8899 开始找空端口，返回实际端口。 */
    fun start(): Int {
        var found: ServerSocket? = null
        for (p in 8899..8935) {
            try {
                found = ServerSocket(p)
                break
            } catch (_: Exception) {
            }
        }
        val ss = found ?: ServerSocket(0)
        server = ss
        port = ss.localPort
        running = true
        thread(isDaemon = true, name = "share-server") {
            while (running) {
                val client = try {
                    ss.accept()
                } catch (_: Exception) {
                    break
                }
                thread(isDaemon = true) { handle(client) }
            }
        }
        return port
    }

    private fun handle(client: Socket) {
        try {
            client.use { c ->
                val input = c.getInputStream()
                val buf = ByteArray(2048)
                val n = input.read(buf)
                val request = if (n > 0) String(buf, 0, n) else ""
                val firstLine = request.lineSequence().firstOrNull() ?: ""
                val isHead = firstLine.startsWith("HEAD ")
                val out = c.getOutputStream()
                if (request.isNotEmpty() && !firstLine.startsWith("GET ") && !isHead) {
                    out.write("HTTP/1.1 405 Method Not Allowed\r\nConnection: close\r\n\r\n".toByteArray())
                    out.flush()
                    return
                }
                val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + mime + "\r\n" +
                    "Content-Length: " + file.length() + "\r\n" +
                    "Content-Disposition: attachment; filename=\"" + file.name + "\"\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(header.toByteArray())
                if (!isHead) {
                    file.inputStream().use { it.copyTo(out, 16 * 1024) }
                }
                out.flush()
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
    }
}

/** 当前 Wi-Fi 的局域网 IPv4 地址；没连 Wi-Fi 时返回 null。 */
internal fun localWifiIp(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (ni in interfaces) {
            if (!ni.isUp || ni.isLoopback) continue
            for (addr in ni.inetAddresses) {
                if (addr is Inet4Address && addr.isSiteLocalAddress) return addr.hostAddress
            }
        }
    } catch (_: Exception) {
    }
    return null
}
