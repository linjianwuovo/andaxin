package com.andaxin.core.net

import com.andaxin.core.concurrency.nowMillis
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.client.request.forms.FormDataContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * iOS 的引擎：Ktor + Darwin（NSURLSession）。语义对齐 Android 那台 OkHttp：
 * 超时（连 15s / 读 20s、单发限时覆写）、是否跟重定向（FifSession 那台关）、
 * cookie 罐由调用方拿 CookieStore 自己管 —— 请求前问 load，响应后喂 save。
 * multipart 没用 Ktor 的表单构造器，按 RFC 手拼字节：一个字段一个文件就是全部需求，
 * 手拼的报文和 OkHttp 那侧逐字节同构，抓包对得上。
 */

actual fun createHttpEngine(config: HttpEngineConfig): HttpEngine = KtorEngine(config)

/** 裸字节体。multipart 那族手拼好后从这里走。 */
private class RawContent(
    private val bytes: ByteArray,
    override val contentType: ContentType?,
) : OutgoingContent.WriteChannelContent() {
    override suspend fun writeTo(channel: ByteWriteChannel) {
        channel.writeFully(bytes)
    }
}

private class KtorEngine(private val config: HttpEngineConfig) : HttpEngine {

    private val client = HttpClient(Darwin) {
        followRedirects = config.followRedirects
        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeoutMs
            requestTimeoutMillis = config.readTimeoutMs
        }
    }

    override suspend fun execute(call: HttpCall, timeoutMs: Long?): HttpReply {
        val host = hostOf(call.url)
        val cookieLine = config.cookieStore
            ?.takeIf { host != null }
            ?.let { store -> store.load(host!!, pathOf(call.url)).takeIf { it.isNotEmpty() } }
            ?.joinToString("; ") { "${it.name}=${it.value}" }

        val response: HttpResponse = try {
            withContext(Dispatchers.Default) {
                client.request(call.url) {
                    method = HttpMethod(call.method)
                    headers {
                        call.headers.forEach { (k, v) -> append(k, v) }
                        cookieLine?.let { append(HttpHeaders.Cookie, it) }
                    }
                    when (val b = call.body) {
                        null -> {}
                        is HttpBody.Form -> setBody(
                            FormDataContent(
                                Parameters.build {
                                    b.fields.forEach { (k, v) -> append(k, v) }
                                },
                            ),
                        )
                        is HttpBody.Text -> setBody(TextContent(b.content, ContentType.parse(b.contentType)))
                        is HttpBody.Upload -> {
                            val boundary = "andaxin-${nowMillis()}"
                            val head = buildString {
                                append("--").append(boundary).append("\r\n")
                                append("Content-Disposition: form-data; name=\"").append(b.field)
                                append("\"; filename=\"").append(b.fileName).append("\"\r\n")
                                append("Content-Type: ").append(b.mime).append("\r\n\r\n")
                            }.encodeToByteArray()
                            val tail = "\r\n--$boundary--\r\n".encodeToByteArray()
                            setBody(
                                RawContent(
                                    head + b.bytes + tail,
                                    ContentType.parse("multipart/form-data; boundary=$boundary"),
                                ),
                            )
                        }
                    }
                    if (timeoutMs != null) timeout { requestTimeoutMillis = timeoutMs }
                }
            }
        } catch (t: Throwable) {
            throw compactNetError(call.url, t)
        }

        if (host != null) {
            config.cookieStore?.let { store ->
                val setCookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
                val parsed = setCookies.mapNotNull(::parseSetCookie)
                if (parsed.isNotEmpty()) store.save(host, parsed)
            }
        }

        return HttpReply(
            status = response.status.value,
            headers = buildList {
                for (name in response.headers.names()) {
                    response.headers.getAll(name)?.forEach { value -> add(name to value) }
                }
            },
            bodyText = response.bodyAsText(),
        )
    }

    private fun hostOf(url: String): String? =
        url.substringAfter("//", "").substringBefore('/').takeIf { it.isNotEmpty() }

    private fun pathOf(url: String): String {
        val noQuery = url.substringBefore('?')
        val path = noQuery.substringAfter("//", "").substringAfter('/', "")
        return "/$path"
    }

    /** 手解 Set-Cookie：name=value; Domain=..; Path=..; Expires=<HTTP 日期>; Max-Age=秒。解析不动就当会话 cookie。 */
    private fun parseSetCookie(line: String): RawCookie? {
        val parts = line.split(';')
        val first = parts.firstOrNull()?.split('=', limit = 2) ?: return null
        if (first.size != 2 || first[0].isBlank()) return null
        var domain = ""
        var path = "/"
        var expiresAt = Long.MAX_VALUE
        for (i in 1 until parts.size) {
            val kv = parts[i].trim().split('=', limit = 2)
            when (kv[0].lowercase()) {
                "domain" -> if (kv.size == 2) domain = kv[1].trim()
                "path" -> if (kv.size == 2) path = kv[1].trim().ifEmpty { "/" }
                "max-age" -> kv.getOrNull(1)?.trim()?.toLongOrNull()?.let { expiresAt = nowMillis() + it * 1000 }
                "expires" -> kv.getOrNull(1)?.trim()?.let { text -> parseHttpDate(text)?.let { expiresAt = it } }
            }
        }
        return RawCookie(first[0].trim(), first[1].trim(), domain, path, expiresAt)
    }

    private val months = listOf(
        "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec",
    )

    /** "Wed, 21 Oct 2026 07:28:00 GMT" → epoch 毫秒。只认这一种格式（RFC 1123），别的回 null。 */
    private fun parseHttpDate(s: String): Long? {
        val m = Regex("""(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})""").find(s) ?: return null
        val (day, mon, year, hh, mm, ss) = m.destructured
        val month = months.indexOf(mon.lowercase().take(3))
        if (month < 0) return null
        val a = (14 - (month + 1)) / 12
        val y = year.toInt() + 4800 - a
        val mo = month + 1 + 12 * a - 3
        val jdn = day.toInt() + (153 * mo + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045
        val epochSec = (jdn - 2440588) * 86400L + hh.toInt() * 3600 + mm.toInt() * 60 + ss.toInt()
        return epochSec * 1000
    }

    /**
     * NSURLSession 抛回来的是**整段 NSError 描述**（他 08:23 那张截图糊满一屏的就是那个），
     * 原样当"错误原因"画到界面上，谁都读不动。这里压成一行：一句人话 + 两个码 + 主机名。
     *
     * 码对照 `<Security/SecureTransport.h>`：外层 -1200 = "TLS 没谈成"；里层那个才是原因 ——
     * **-9816 = errSSLClosedNoNotify，服务器一个字没告就把连接关了**。证书那类是 -9813/-9814/-9815，
     * 版本/套件谈不拢是 -9824/-9836。所以这一条既不是我们证书装错，也不是 ATS 拦的：
     * 10-27 凌晨我从这台机对着 `ywlz.aiit.edu.cn` 现打了一遍，TLS 1.2 通、1.1 与 1.3 都被它拒
     * （拒 1.3 时回的是干净的 alert 70，不是掐连接）—— 客户端同时带 1.2/1.3 时落 1.2 没问题，
     * 所以真机上那次 -9816 是**它主动断的**，不是版本谈不拢。
     */
    private fun compactNetError(url: String, t: Throwable): Exception {
        val text = buildString {
            append(t.message.orEmpty()).append(' ')
            var c: Throwable? = t.cause
            while (c != null) { append(c.message.orEmpty()).append(' '); c = c.cause }
        }
        val outer = Regex("""Code=(-?\d+)""").find(text)?.groupValues?.get(1)
        val inner = Regex("""kCFStreamErrorCodeKey=(-?\d+)""").find(text)?.groupValues?.get(1)
        val why = when (inner) {
            "-9816" -> "服务器在 TLS 中途把连接掐了（没告而终）"
            "-9813", "-9814", "-9815" -> "校方那张证书系统不认（链或有效期）"
            "-9824", "-9836" -> "TLS 版本/加密套件谈不拢"
            "-1009", "-1003" -> "这台设备现在连不上那台服务器"
            "-1001" -> "那台服务器没在限时内答话"
            else -> null
        }
        val host = hostOf(url).orEmpty()
        val parts = listOfNotNull(
            why,
            "码 ${outer ?: "?"}/${inner ?: "-"}",
            host.ifBlank { null },
        )
        return Exception("这一发请求没成：" + parts.joinToString(" · "))
    }
}
