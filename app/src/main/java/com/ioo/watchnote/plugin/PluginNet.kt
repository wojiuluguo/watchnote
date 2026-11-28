package com.ioo.watchnote.plugin

import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 一次联网请求的结果。
 *
 * [code] 是 HTTP 状态码（0 表示根本没发出去）；[error] 非空时 [body] 没有意义。
 * error 只有几种，插件照着这几种做提示就行：
 * timeout / offline / dns / blocked / too_large / too_frequent / no_permission / bad_url / unknown。
 */
class HttpResult @JvmOverloads constructor(
    val code: Int,
    val body: String,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null && code in 200..299

    override fun toString(): String =
        if (error != null) "HttpResult(error=" + error + ")" else "HttpResult(" + code + ", " + body.length + " chars)"
}

/**
 * 宿主联网渠道（0.4.5 起）。
 *
 * 只给一条路：插件要联网就走这里，宿主负责发、负责限、负责拦。
 * 不这么做的话，外置插件是普通 Java 字节码，自己 new 一个 URL 就能连，宿主连日志都拿不到。
 * 现在这条路的好处是：用户能看见（安装时要声明 net 权限）、能关掉（设置里单独关）、
 * 出了问题日志里有插件 id，跑飞了还有个每分 30 次的限速兜着。
 *
 * 设计上刻意做成「异步 + 回调」：手表主线程很金贵，插件不能在上面等网络。
 * 回调统一切回主线程，插件在回调里可以直接改数据、调 invalidate()。
 *
 * 硬规则：
 * 1. 只允许 http / https；
 * 2. 不允许本机和内网地址（防插件拿手表当跳板扫家里路由器）；
 * 3. 响应最多 256KB，超了直接报 too_large，不把整块内存吃掉；
 * 4. 超时 5~30 秒，默认 10 秒；
 * 5. 每个插件 30 次/分、同时最多 2 个；离开请求跑完就结束，不做后台长连接。
 */
class NetApi internal constructor(
    private val pluginId: String,
    private val permissions: Set<Permission>,
) {
    private val inflight = AtomicInteger(0)
    private val recentCalls = ArrayList<Long>()

    /** 设置里把这个插件的联网关掉后，这里立刻失效（用户说了算）。 */
    var enabled: Boolean = true

    @JvmOverloads
    fun get(url: String, timeoutMs: Int = 10000, onDone: (HttpResult) -> Unit) {
        request("GET", url, null, null, timeoutMs, onDone)
    }

    @JvmOverloads
    fun post(url: String, body: String, contentType: String? = null, timeoutMs: Int = 10000, onDone: (HttpResult) -> Unit) {
        val headers = if (contentType.isNullOrEmpty()) null else mapOf("Content-Type" to contentType)
        request("POST", url, headers, body, timeoutMs, onDone)
    }

    @JvmOverloads
    fun request(
        method: String,
        url: String,
        headers: Map<String, String>? = null,
        body: String? = null,
        timeoutMs: Int = 10000,
        onDone: (HttpResult) -> Unit,
    ) {
        if (!permissions.contains(Permission.NET)) {
            deliver(onDone, HttpResult(0, "", "no_permission"))
            return
        }
        if (!enabled) {
            deliver(onDone, HttpResult(0, "", "blocked"))
            return
        }
        val parsed = runCatching { URL(url) }.getOrNull()
        if (parsed == null) {
            deliver(onDone, HttpResult(0, "", "bad_url"))
            return
        }
        val scheme = parsed.protocol.lowercase()
        if (scheme != "http" && scheme != "https") {
            deliver(onDone, HttpResult(0, "", "bad_url"))
            return
        }
        if (isInternalHost(parsed.host)) {
            deliver(onDone, HttpResult(0, "", "blocked"))
            return
        }
        if (!acquireSlot()) {
            deliver(onDone, HttpResult(0, "", "too_frequent"))
            return
        }
        val timeout = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        val methodName = method.uppercase()
        inflight.incrementAndGet()
        POOL.execute {
            val result = try {
                runCatching { execute(methodName, parsed, headers, body, timeout) }
                    .getOrElse { error -> HttpResult(0, "", classify(error)) }
            } finally {
                inflight.decrementAndGet()
            }
            deliver(onDone, result)
        }
    }

    private fun execute(
        method: String,
        url: URL,
        headers: Map<String, String>?,
        body: String?,
        timeoutMs: Int,
    ): HttpResult {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WristNote/" + PluginApi.MODULE_VERSION + " plugin/" + pluginId)
            headers?.forEach { (k, v) -> setRequestProperty(k, v) }
            if (body != null && method != "GET") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null && method != "GET") {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = if (stream == null) "" else readCapped(stream)
            return HttpResult(code, text, null)
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** 最多读 256KB；再多就报太大，不把内存吃满。 */
    private fun readCapped(stream: java.io.InputStream): String {
        val out = ByteArrayOutputStream(4096)
        val buffer = ByteArray(8192)
        var total = 0
        stream.use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                total += n
                if (total > MAX_BYTES) throw TooLargeException()
                out.write(buffer, 0, n)
            }
        }
        return out.toString("UTF-8")
    }

    private fun classify(error: Throwable): String = when (error) {
        is TooLargeException -> "too_large"
        is java.net.SocketTimeoutException -> "timeout"
        is java.net.UnknownHostException, is java.net.NoRouteToHostException -> "dns"
        is java.net.ConnectException -> "offline"
        else -> "unknown"
    }

    private fun acquireSlot(): Boolean {
        val now = System.currentTimeMillis()
        synchronized(recentCalls) {
            recentCalls.removeAll { now - it > 60_000L }
            if (recentCalls.size >= MAX_PER_MINUTE) return false
            recentCalls.add(now)
            return true
        }
    }

    private fun deliver(onDone: (HttpResult) -> Unit, result: HttpResult) {
        MAIN.post { runCatching { onDone(result) } }
    }

    private class TooLargeException : Exception()

    companion object {
        private const val MIN_TIMEOUT_MS = 5_000
        private const val MAX_TIMEOUT_MS = 30_000
        private const val MAX_BYTES = 256 * 1024
        private const val MAX_PER_MINUTE = 30
        private val POOL = Executors.newFixedThreadPool(2) { r ->
            Thread(r, "wristnote-plugin-net").apply { isDaemon = true }
        }
        private val MAIN = Handler(Looper.getMainLooper())

        /** 本机 / 内网地址一律拒掉：插件没有理由从手表上访问这些。 */
        internal fun isInternalHost(rawHost: String?): Boolean {
            val host = rawHost?.lowercase() ?: return true
            if (host.isEmpty()) return true
            if (host == "localhost" || host.endsWith(".local")) return true
            if (host.contains(':')) return true // IPv6 一律不放行，手表上没有需要它的场景
            val parts = host.split('.')
            if (parts.size == 4 && parts.all { p -> p.toIntOrNull() != null }) {
                val a = parts[0].toInt()
                val b = parts[1].toInt()
                if (a == 127 || a == 10 || a == 0) return true
                if (a == 192 && b == 168) return true
                if (a == 172 && b in 16..31) return true
                if (a == 169 && b == 254) return true
            }
            return false
        }
    }
}
