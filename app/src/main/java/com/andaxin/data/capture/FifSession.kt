package com.andaxin.data.capture

import com.andaxin.core.net.Http
import com.andaxin.core.store.SessionStore
import com.andaxin.core.store.Store
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/*
 * AI 课堂那套（FIF 平台）的会话层。
 *
 * 这一层单独开一个文件，是因为它跟 in.aiit 那一族**完全是两套鉴权**：
 * 校内的 access_token 不能直接拿去问 FIF，要先经过一次单点登录换成 FIF 自己的 JWT，
 * 而且 FIF 判"你是谁"同时看三样 —— 请求头 `authorization`、`Visit-Type`、还有 cookie 里的 `SESSION`。
 * 少一样它不报 401，就给你 HTTP 200 + `data:null`，看着像"这门课没内容"。
 *
 * 【取证】归属按本仓的三等同规矩标：
 * - ③（目标 App 发出、被我们自己抓到的报文）：SSO 那三跳的链路、`getAiktUserIdByMemberId` 的
 *   响应形状、`authorization: Basic <jwt>` + `Visit-Type: mobile` 这对头 —— 出自旧项目里
 *   2026-10-13 那一轮真机抓包与随后写下的实测注释（`lightxin-main` 的 `FifSessionManager`）。
 *   那是这个项目自己的取证结果，按 10-20 的授权可以用。
 * - **本轮（10-20 凌晨）我没有再跑通一次**：这台机器上四把校内 token 全被校方清了，
 *   SSO 第一步就要校内的 access_token。所以下面每一处"应当如此"都还欠一次我自己的现打，
 *   等有机内会话时按 `docs/接入清单.md` 的顺序补验，补一次改一处。
 */

/** FIF 那侧认的三个 id。名字像，来源完全不同，混了就是一脸问号。 */
data class FifIds(
    /** 短学号（8 位）。签到信息、正在上课记录用它。 */
    val studentId: String,
    /** 登录学号（10 位）。数字码签到、微课、作业列表用它。 */
    val userName: String,
    val schoolId: String,
    /** 32 位成员 id，从 FIF JWT 的第二段里解出来。课件/小组/课表那几发用它当 `userId`/`mid`/`studentId`。 */
    val memberUserId: String,
)

class FifSession(
    private val store: Store,
    private val school: SessionStore,
) {

    private val mutex = Mutex()

    /**
     * 只活在进程里的 cookie 罐。按 host 分桶，同名同 path 同 domain 覆盖，过期即丢 ——
     * OkHttp 自带的示例实现是全局一张 map，跨域名会串。
     */
    private val jar = java.util.concurrent.ConcurrentHashMap<String, MutableList<Cookie>>()

    private val client: OkHttpClient by lazy {
        Http.client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            // 第一跳要自己读 302 的 Location，不能让 OkHttp 替我跟过去
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(
                object : CookieJar {
                    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                        val keep = cookies.filter { it.expiresAt == Long.MAX_VALUE || it.expiresAt > System.currentTimeMillis() }
                        if (keep.isEmpty()) return
                        jar.getOrPut(url.host) { mutableListOf() }.let { list ->
                            keep.forEach { c ->
                                list.removeAll { old ->
                                    old.name == c.name && old.path == c.path && old.domain == c.domain
                                }
                                list.add(c)
                            }
                        }
                    }

                    override fun loadForRequest(url: HttpUrl): List<Cookie> =
                        jar[url.host].orEmpty().filter { it.matches(url) }
                },
            )
            .build()
    }

    /** 有没有一把还没过期的 FIF token + 一条 SESSION cookie。两样都得有，缺一律重登。 */
    suspend fun isSessionValid(): Boolean {
        val p = store.snapshot()
        val token = store.peekString(p, KEY_FIF_TOKEN).orEmpty()
        if (token.isBlank()) return false
        if (jwtExpiresAt(token)?.let { System.currentTimeMillis() / 1000 + SKEW >= it } == true) return false
        // token 过期后 SESSION 还在罐里，会"看着有效"—— 这就是"AI 课堂时好时坏"那个现象的根
        return jar[HOST_STTP]?.any { it.name == "SESSION" && it.value.isNotBlank() } == true
    }

    suspend fun ids(): FifIds? {
        val p = store.snapshot()
        val student = store.peekString(p, KEY_STUDENT_ID).orEmpty()
        val user = store.peekString(p, KEY_USER_NAME).orEmpty()
        val schoolId = store.peekString(p, KEY_SCHOOL_ID).orEmpty()
        if (student.isBlank() && user.isBlank()) return null
        return FifIds(
            studentId = student,
            userName = user,
            schoolId = schoolId,
            memberUserId = store.peekString(p, KEY_MEMBER_ID).orEmpty(),
        )
    }

    /** 主站那套的头：`authorization: Basic <jwt>` + `Visit-Type: mobile`。 */
    suspend fun authHeader(): String? = store.peekString(store.snapshot(), KEY_FIF_TOKEN)
        ?.takeIf { it.isNotBlank() }
        ?.let { "Basic $it" }

    /**
     * 资源中心（`resc.fifedu.com`）那发要的是**裸 JWT**：带 `Basic ` 前缀它不认，
     * 而且不能带 `Visit-Type`。主站的习惯直接搬过去，回的是 200 + `data:null`。
     */
    suspend fun bareJwt(): String? = store.peekString(store.snapshot(), KEY_FIF_TOKEN)
        ?.takeIf { it.isNotBlank() }

    /**
     * 保证有一把能用的 FIF 会话。已经有效就一个字节都不发。
     *
     * 拿锁之后**还要再查一遍**：首页预热和进页面加载曾经同一秒各跑了一遍完整 SSO，白多花 800ms。
     */
    suspend fun ensure(): Result<FifIds> {
        if (isSessionValid()) ids()?.let { return Result.success(it) }
        return mutex.withLock {
            if (isSessionValid()) {
                ids()?.let { return@withLock Result.success(it) }
            }
            performSso()
        }
    }

    /** 三跳。任何一跳失败都回 failure，带得上原因就带上，不静默降级。 */
    suspend fun performSso(): Result<FifIds> {
        val inner = school.identity()
            ?: return Result.failure(IOException("校内还没登录，FIF 的单点登录第一步就没有 access_token"))

        // 第一跳：aiitpass 换 FIF 的 JWT。token 值在 302 的 Location 的 query 里。
        val ssoUrl = "$HOST_PASS/iplat-pass-aiit/h5/login?" +
            "access_token=${urlEnc(inner.accessToken)}" +
            "&_userCode=${urlEnc(inner.userCode)}" +
            "&code=${urlEnc(inner.userCode)}" +
            "&userCode=${urlEnc(inner.userCode)}" +
            "&_userName=${urlEnc(inner.userName)}" +
            "&_userType=${urlEnc(inner.userType)}" +
            "&appId=${School.APP_ID}" +
            "&returnFromIscToAppFunc=ReturnDefault"
        val location = runCatching {
            get(ssoUrl, "Location")
        }.getOrElse { return Result.failure(it) }

        /*
         * 这里有个坑值得单独写一行：`?token=` 值为空时，query 解析出来是**空串不是 null**，
         * 只判 null 就会把"没拿到 token"当成登录成功存下来，之后每一发都回"鉴权失败 Token 为空"，
         * 再触发重登，形成几十次往返的慢循环。所以必须 isNotBlank 过滤。
         */
        val jwt = tokenFromLocation(location)
            ?: return Result.failure(IOException("单点登录没回 token（Location：${location?.take(90) ?: "没有跳转"}）"))

        // 第二跳：只为让服务器 Set-Cookie 出 SESSION
        runCatching { getRaw("$HOST_STTP/studycenter-nh5/?token=${urlEnc(jwt)}&app=axx") }

        // 第三跳：换三个 id。失败不致命 —— 课程列表照样能拉，只是签到/小组那几发没身份
        val ids = runCatching { fetchIds(jwt) }.getOrElse {
            store.setText(KEY_FIF_TOKEN, jwt)
            saveIdentityCookies(jwt, ids = null)
            return Result.failure(IOException("FIF 身份接口没通：${it.message ?: it.javaClass.simpleName}"))
        }
        store.setMany(
            KEY_FIF_TOKEN to jwt,
            KEY_STUDENT_ID to ids.studentId,
            KEY_USER_NAME to ids.userName,
            KEY_SCHOOL_ID to ids.schoolId,
            KEY_MEMBER_ID to ids.memberUserId,
        )
        saveIdentityCookies(jwt, ids)
        return Result.success(ids)
    }

    suspend fun clear() {
        jar.clear()
        store.setMany(
            KEY_FIF_TOKEN to null,
            KEY_STUDENT_ID to null,
            KEY_USER_NAME to null,
            KEY_SCHOOL_ID to null,
            KEY_MEMBER_ID to null,
        )
    }

    private suspend fun fetchIds(jwt: String): FifIds {
        var last: Throwable? = null
        // 退避三次：600ms、1200ms。这一发挂了只降级显示，不该把整块 AI 课堂带走
        for (attempt in 0 until 3) {
            if (attempt > 0) delay(600L * attempt)
            runCatching {
                val body = postForm(
                    "$HOST_STTP/studycenter/mobile/common/getAiktUserIdByMemberId",
                    emptyList(),
                    "Basic $jwt",
                )
                val root = JSONObject(body)
                val data = root.optJSONObject("data") ?: JSONObject()
                FifIds(
                    studentId = data.optString("id"),
                    userName = data.optString("userName").ifBlank { school.identity()?.userCode.orEmpty() },
                    schoolId = data.optString("schoolId"),
                    memberUserId = memberFromJwt(jwt).orEmpty(),
                )
            }.onSuccess { return it }.onFailure { last = it }
        }
        throw last ?: IOException("身份接口三次都没通")
    }

    /** 中文姓名进 cookie 前必须转 ASCII，否则 OkHttp 直接抛 `Unexpected char 0x… in Cookie value`，把整条登录链炸掉。 */
    private fun saveIdentityCookies(jwt: String, ids: FifIds?) {
        val name = ids?.userName?.takeIf { it.isNotBlank() } ?: return
        val pairs = listOf(
            "id" to ids.studentId,
            "studentId" to ids.studentId,
            "currentUserName" to URLEncoder.encode(name, "UTF-8"),
        )
        val list = pairs.filter { it.second.isNotBlank() }.map { (k, v) ->
            Cookie.Builder().name(k).value(v).domain("fifedu.com").path("/").build()
        }
        jar[HOST_STTP] = mutableListOf<Cookie>().apply {
            addAll(jar[HOST_STTP].orEmpty().filterNot { n -> list.any { it.name == n.name } })
            addAll(list)
        }
    }

    /**
     * 资源中心（`resc.fifedu.com`）那一发专用：带**裸 JWT**、**不带 `Visit-Type`**、
     * 带 `Referer/Origin: mresc.fifedu.com`。照主站习惯带头会回 200 + `data:null`。
     * 【取证】③ 出自旧项目 2026-10-13 抓到的官方那一发。
     */
    suspend fun getResc(url: String, jwt: String): String {
        val req = Request.Builder().url(url).get()
            .header("User-Agent", WEB_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("authorization", jwt)
            .header("X-Requested-With", "com.andaxin")
            .header("Referer", "$HOST_RESC/")
            .header("Origin", HOST_RESC)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("资源中心回了 ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private suspend fun get(url: String, header: String): String? {
        val req = Request.Builder().url(url).get().header("User-Agent", WEB_UA).build()
        client.newCall(req).execute().use { resp -> return resp.header(header) }
    }

    private suspend fun getRaw(url: String) {
        val req = Request.Builder().url(url).get().header("User-Agent", WEB_UA).build()
        client.newCall(req).execute().use { it.body?.string() }
    }

    suspend fun postForm(url: String, fields: List<Pair<String, String>>, auth: String?): String {
        val form = okhttp3.FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val builder = Request.Builder().url(url).post(form)
            .header("User-Agent", WEB_UA)
            .header("Accept", "application/json, text/plain, */*")
        if (auth != null) builder.header("authorization", auth)
        builder.header("Visit-Type", "mobile")
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("FIF 回了 ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    /** 带 JWT 发一发 GET，回根节点。 */
    suspend fun getJson(url: String, auth: String? = null): JSONObject {
        val builder = Request.Builder().url(url).get()
            .header("User-Agent", WEB_UA)
            .header("Accept", "application/json, text/plain, */*")
        builder.header("Visit-Type", "mobile")
        (auth ?: authHeader())?.let { builder.header("authorization", it) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("FIF 回了 ${resp.code}")
            return JSONObject(resp.body?.string().orEmpty())
        }
    }

    private companion object {
        const val HOST_PASS = "https://aiitpass.fifedu.com"
        const val HOST_STTP = "https://sttp.fifedu.com"

        /** 播放页真正的 referer 是这个子域，不是 resc 本身。 */
        const val HOST_RESC = "https://mresc.fifedu.com"

        /** 手机 WebView 那一串。FIF 有些口认 UA，给 okhttp 默认串会被当爬虫。 */
        const val WEB_UA =
            "Mozilla/5.0 (Linux; Android 17; M2011K2C Build/CP2A.260605.016; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/149.0.7827.91 Mobile Safari/537.36"

        const val SKEW = 60L

        const val KEY_FIF_TOKEN = "fif.token"
        const val KEY_STUDENT_ID = "fif.studentId"
        const val KEY_USER_NAME = "fif.userName"
        const val KEY_SCHOOL_ID = "fif.schoolId"
        const val KEY_MEMBER_ID = "fif.memberId"

        fun urlEnc(s: String): String = URLEncoder.encode(s, "UTF-8")

        /** Location 里取 token：先按 query 正规解，解不出来退回正则（那条 Location 见过不规范编码）。 */
        fun tokenFromLocation(location: String?): String? {
            if (location.isNullOrBlank()) return null
            val fromUrl = runCatching {
                location.toHttpUrl().queryParameter("token")
            }.getOrNull()
            return fromUrl?.takeIf { it.isNotBlank() }
                ?: Regex("[?&]token=([^&]+)").find(location)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        }

        /** JWT 第二段 base64url 解出来，取 `memberId` 或 `memberUserId`。 */
        fun memberFromJwt(jwt: String): String? {
            val parts = jwt.split('.')
            if (parts.size < 2) return null
            val json = runCatching {
                String(
                    java.util.Base64.getUrlDecoder().decode(
                        parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='),
                    ),
                )
            }.getOrNull() ?: return null
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            return listOf("memberId", "memberUserId", "userId").asSequence()
                .map { o.optString(it) }
                .firstOrNull { it.isNotBlank() && it.length >= 16 }
        }

        /** 解 JWT 的 `exp`（秒）。解不出来按"未到期"处理，别因为解析失败把人关在登录循环里。 */
        fun jwtExpiresAt(jwt: String): Long? {
            val parts = jwt.split('.')
            if (parts.size < 2) return null
            val json = runCatching {
                String(
                    java.util.Base64.getUrlDecoder().decode(
                        parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='),
                    ),
                )
            }.getOrNull() ?: return null
            return runCatching { JSONObject(json).optLong("exp") }.getOrNull()?.takeIf { it > 0 }
        }
    }
}
