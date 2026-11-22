package com.ioo.watchnote.plugin

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * 「装插件」的局域网接收服务（4.1.15）。
 *
 * 为什么做成网页：手表屏幕小、键盘难按，插件包一般又在电脑或手机上。
 * 所以手表开一个很小的 HTTP 服务，手机浏览器打开一个地址，页面上同时给两件事：
 *   1. 传文件：手机选 .wplugin 上传，手表收到后先弹清单，用户点「安装」才真的装；
 *   2. 填网址：把插件包的下载地址交给手表，手表自己去下（手机不用先下）。
 *
 * 安全边界：
 *   - 只监听局域网端口，只认 GET / 和两个 POST；
 *   - 上传和下载都限制 4 MB（和安装器 MAX_PACKAGE_BYTES 一致）；
 *   - 收到的文件只写进缓存目录，包结构检查、验签、解压全部交给 ExternalPluginInstaller，
 *     这一层不认识插件格式，也不执行任何东西。
 */
internal class PluginReceiverServer(
    private val inboxDir: File,
    private val maxPackageBytes: Long = 4L * 1024 * 1024,
    private val onPackage: (File) -> Unit,
) {

    private var server: ServerSocket? = null

    @Volatile
    private var running = false

    @Volatile
    var port: Int = 0
        private set

    /** 从 8940 开始找空端口（避开「发到手机」用的 8899），返回实际端口。 */
    fun start(): Int {
        if (running) return port
        var found: ServerSocket? = null
        for (p in 8940..8976) {
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
        thread(isDaemon = true, name = "plugin-receiver") {
            while (running) {
                val client = try {
                    ss.accept()
                } catch (_: Exception) {
                    break
                }
                thread(isDaemon = true, name = "plugin-receiver-conn") { handle(client) }
            }
        }
        return port
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
    }

    private fun handle(client: Socket) {
        try {
            client.use { c ->
                c.soTimeout = 30000
                val input = c.getInputStream()
                val head = readHead(input) ?: return
                val requestLine = head.lineSequence().firstOrNull().orEmpty()
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]
                val out = c.getOutputStream()
                if (headerValue(head, "expect")?.contains("100-continue", ignoreCase = true) == true) {
                    out.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                    out.flush()
                }
                val length = headerValue(head, "content-length")?.toLongOrNull() ?: 0L
                val contentType = headerValue(head, "content-type").orEmpty()
                when {
                    method == "GET" && (path == "/" || path.startsWith("/?")) ->
                        respond(out, 200, PICK_PAGE)

                    method == "POST" && path == "/upload" ->
                        handleUpload(input, out, length, contentType)

                    method == "POST" && path == "/url" ->
                        handleUrl(input, out, length)

                    else -> respond(
                        out,
                        404,
                        simplePage("没有这个地址", "回手表上重新点一次「手机传 / 网址安装」。"),
                    )
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun handleUpload(input: InputStream, out: OutputStream, length: Long, contentType: String) {
        if (length <= 0L) {
            respond(out, 400, simplePage("没收到文件", "先在手机上选一个 .wplugin 文件再点上传。"))
            return
        }
        if (length > maxPackageBytes + 128L * 1024L) {
            respond(out, 413, simplePage("文件太大", "插件包上限是 4 MB，这个超过太多了。"))
            return
        }
        val body = readBody(input, length.toInt())
        val part = multipartFile(body, contentType)
        if (part == null) {
            respond(out, 400, simplePage("没认出上传的文件", "换一个手机浏览器再试，或者回到手表改用「填网址」。"))
            return
        }
        val (rawName, bytes) = part
        if (bytes.isEmpty()) {
            respond(out, 400, simplePage("文件是空的", "重新选一次文件。"))
            return
        }
        if (bytes.size.toLong() > maxPackageBytes) {
            respond(out, 413, simplePage("文件太大", "插件包上限是 4 MB。"))
            return
        }
        val file = save(safeName(rawName), bytes)
        if (file == null) {
            respond(out, 500, simplePage("手表存不下", "手表存储可能满了，清一点空间再试。"))
            return
        }
        onPackage(file)
        respond(
            out,
            200,
            simplePage(
                "已传到手表",
                "抬手看手表：上面会弹出这个插件的清单，点「安装」才真的装。<br>还想传就刷新本页再来一次。",
            ),
        )
    }

    private fun handleUrl(input: InputStream, out: OutputStream, length: Long) {
        if (length <= 0L || length > 16384L) {
            respond(out, 400, simplePage("没收到网址", "把插件包的下载地址粘进去再点按钮。"))
            return
        }
        val bodyText = String(readBody(input, length.toInt()), Charsets.UTF_8)
        val raw = bodyText.split('&').firstOrNull { it.startsWith("url=") }?.substring(4).orEmpty()
        val address = try {
            URLDecoder.decode(raw, "UTF-8")
        } catch (_: Exception) {
            raw
        }.trim()
        if (!address.startsWith("http://") && !address.startsWith("https://")) {
            respond(out, 400, simplePage("网址不对", "只支持 http:// 或 https:// 开头的地址。"))
            return
        }
        val downloaded = download(address)
        if (downloaded == null) {
            respond(
                out,
                502,
                simplePage(
                    "手表没下下来",
                    "检查两件事：①地址是不是直接指向 .wplugin 文件；②手表连的 Wi-Fi 能不能上网。",
                ),
            )
            return
        }
        onPackage(downloaded)
        respond(out, 200, simplePage("手表已下载", "抬手看手表：确认清单点「安装」才真的装。"))
    }

    private fun download(address: String): File? {
        var conn: HttpURLConnection? = null
        var target: File? = null
        return try {
            val url = URL(address)
            val c = url.openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 8000
            c.readTimeout = 20000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "WristNote/4.1.15")
            if (c.responseCode !in 200..299) {
                null
            } else {
                val dir = ensureDir() ?: return null
                val file = File(dir, System.currentTimeMillis().toString() + "-" + safeName(url.path.substringAfterLast('/')))
                target = file
                c.inputStream.use { source ->
                    file.outputStream().use { sink ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val n = source.read(buffer)
                            if (n <= 0) break
                            total += n
                            if (total > maxPackageBytes) throw IllegalStateException("包超过 4 MB")
                            sink.write(buffer, 0, n)
                        }
                    }
                }
                if (file.length() > 0L) file else null
            }
        } catch (_: Throwable) {
            target?.delete()
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun save(name: String, bytes: ByteArray): File? {
        val dir = ensureDir() ?: return null
        val file = File(dir, System.currentTimeMillis().toString() + "-" + name)
        return try {
            file.writeBytes(bytes)
            file
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private fun ensureDir(): File? {
        if (inboxDir.isDirectory) return inboxDir
        return if (inboxDir.mkdirs() || inboxDir.isDirectory) inboxDir else null
    }

    private fun readBody(input: InputStream, length: Int): ByteArray {
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(data, offset, length - offset)
            if (n <= 0) break
            offset += n
        }
        return if (offset == length) data else data.copyOfRange(0, offset)
    }

    /** 读到空行为止的请求头；只读 8 KB，防止被乱发的东西拖住。 */
    private fun readHead(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        val tail = IntArray(4) { -1 }
        while (buffer.size() < 8192) {
            val b = input.read()
            if (b < 0) break
            buffer.write(b)
            tail[0] = tail[1]
            tail[1] = tail[2]
            tail[2] = tail[3]
            tail[3] = b
            if (tail[0] == 13 && tail[1] == 10 && tail[2] == 13 && tail[3] == 10) break
        }
        if (buffer.size() == 0) return null
        return buffer.toString("ISO-8859-1")
    }

    private fun headerValue(head: String, name: String): String? {
        for (line in head.lineSequence()) {
            val index = line.indexOf(':')
            if (index <= 0) continue
            if (line.substring(0, index).trim().equals(name, ignoreCase = true)) {
                return line.substring(index + 1).trim()
            }
        }
        return null
    }

    /**
     * 从 multipart/form-data 里取第一个文件部分。
     * 用 ISO-8859-1 把整段当「每字节一个字符」的字符串来定位边界，下标可以直接当字节下标用，
     * 这样正文是二进制也不会被字符集弄坏。
     */
    private fun multipartFile(body: ByteArray, contentType: String): Pair<String, ByteArray>? {
        val boundaryIndex = contentType.indexOf("boundary=")
        if (boundaryIndex < 0) return null
        val boundary = contentType.substring(boundaryIndex + 9).trim().trim('"')
        if (boundary.isEmpty()) return null
        val text = String(body, Charsets.ISO_8859_1)
        var cursor = 0
        while (true) {
            val start = text.indexOf("--" + boundary, cursor)
            if (start < 0) return null
            val headerStart = text.indexOf("\r\n", start)
            if (headerStart < 0) return null
            val headerEnd = text.indexOf("\r\n\r\n", headerStart)
            if (headerEnd < 0) return null
            val headers = text.substring(headerStart + 2, headerEnd)
            val dataStart = headerEnd + 4
            val next = text.indexOf("\r\n--" + boundary, dataStart)
            if (next < 0) return null
            val nameMatch = Regex("filename=\"([^\"]*)\"").find(headers)
            if (nameMatch != null) {
                return nameMatch.groupValues[1] to body.copyOfRange(dataStart, next)
            }
            cursor = next + 2
        }
    }

    private fun safeName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "").trim()
        val name = if (cleaned.isEmpty()) "plugin.wplugin" else cleaned.take(60)
        return if (name.endsWith(".wplugin") || name.endsWith(".zip")) name else name + ".wplugin"
    }

    private fun respond(out: OutputStream, code: Int, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            else -> "OK"
        }
        val header = "HTTP/1.1 " + code + " " + reason + "\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: " + body.size + "\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        try {
            out.write(header.toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.flush()
        } catch (_: Exception) {
        }
    }

    private fun simplePage(title: String, message: String): String =
        "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>" + title + "</title></head>" +
            "<body style=\"font-family:-apple-system,'Segoe UI',sans-serif;background:#101418;color:#eef3f8;" +
            "padding:24px;line-height:1.7\">" +
            "<h2 style=\"margin:0 0 10px\">" + title + "</h2>" +
            "<p style=\"color:#9aa4ae\">" + message + "</p>" +
            "<p><a href=\"/\" style=\"color:#7fb2ff\">回上一页</a></p>" +
            "</body></html>"

    companion object {
        /** 手机打开的页面：上面传文件，下面填网址。都是普通表单，不需要 JS。 */
        private const val PICK_PAGE = """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>腕记 · 装插件</title></head>
<body style="font-family:-apple-system,'Segoe UI',sans-serif;background:#101418;color:#eef3f8;padding:22px;line-height:1.7;max-width:560px;margin:0 auto">
<h2 style="margin:0 0 4px">腕记 · 装插件</h2>
<p style="color:#9aa4ae;margin:0 0 20px">这个页面是手表自己开的。无论用下面哪种方式，手表上都会先弹出插件清单，点「安装」才真的装。</p>
<h3 style="margin:20px 0 8px">方式一：从手机传文件</h3>
<form method="post" action="/upload" enctype="multipart/form-data">
<input type="file" name="f" accept=".wplugin,.zip,application/zip,application/octet-stream" required
 style="display:block;width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid #2b333d;background:#161c23;color:#eef3f8">
<button type="submit" style="margin-top:12px;width:100%;padding:14px;border:0;border-radius:12px;background:#3d7bfd;color:#fff;font-size:16px">传到手表</button>
</form>
<h3 style="margin:26px 0 8px">方式二：让手表去下载</h3>
<p style="color:#9aa4ae;margin:0 0 8px;font-size:14px">把插件包的直链粘进来，手表自己去下，手机不用先下。</p>
<form method="post" action="/url">
<input type="text" name="url" inputmode="url" placeholder="https://…/xxx.wplugin" required
 style="display:block;width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid #2b333d;background:#161c23;color:#eef3f8">
<button type="submit" style="margin-top:12px;width:100%;padding:14px;border:0;border-radius:12px;background:#2f9e6a;color:#fff;font-size:16px">让手表下载</button>
</form>
<p style="color:#6d7681;font-size:13px;margin-top:26px">插件包是 .wplugin 文件——其实就是一个 zip，里面有 plugin.json、classes.dex，可以带 assets/ 和作者签名 plugin.sig。上限 4 MB。</p>
</body></html>"""
    }
}
