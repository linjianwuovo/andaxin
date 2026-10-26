package com.andaxin.data.capture

import com.andaxin.core.net.Http
import com.andaxin.core.store.SessionStore
import com.andaxin.core.store.Store
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 * 而 FIF 主站那几发认的就两个头：`authorization: Basic <jwt>` + `Visit-Type: mobile`。
 * 少一个它不报 401，就给你 HTTP 200 + `data:null`，看着像"这门课没内容"。
 *
 * 【取证 · 归属按本仓的三等同规矩标】
 * - ③（目标 App 发出、被我们自己抓到的报文）：SSO 那三跳的链路形状、`getAiktUserIdByMemberId`
 *   的响应骨架、`authorization: Basic <jwt>` + `Visit-Type: mobile` 这对头 ——
 *   出自 2026-10-13 那一轮真机抓包（清点表在 `lxtmp/契约_AI课堂抓包.md`）。
 * - ②（我们自己的客户端发出去、看到回的）：**10-20 上午整条链自己现打过一遍**
 *   （`lxtmp/adx_probe_fif.py`、`adx_probe_fif2.py`、`adx_probe_fif3.py`，用的是回放换来的活 token）。
 *   三跳全通，`myClassroom` 回 15 门课，签到两发、`getOneCourse`、`getFirstPageOfCourse`、
 *   直录播 76 条都拿到了真数据。这一层下面的注释凡写"实测"都是指这一轮。
 *   **唯一没验成的**：手机里这个 App 自己走一遍（PC 上通了，界面上还没读到过）。
 */

/**
 * FIF 那侧认的几个 id。名字像，来源完全不同，混了就是一脸问号。
 *
 * 【取证 · ② 我今天现打到的】`getAiktUserIdByMemberId` 回的 `data{}` 一条不差是：
 * `id="<8 位 FIF 账号 id>"(8) userType="student" name=<姓名> avatar phone="" email="" collegeId="3212"(4)
 * gender number="<10 位学号>"(10) userName="aiit+学号"(14) userId=<32 hex> epasUserId=""
 * schoolId="2811000226000001678"(19) schoolName="安徽信息工程学院"`。
 * 注意 **`userName` 不是学号**，是 `aiit` 前缀 + 学号那个 14 位登录名；10 位学号在 `number`。
 * 清点表里 `userName:str(14)` 那几发（B21/C28/C31）要的就是前者。
 */
data class FifIds(
    /** 短学号（8 位，样例 `<8 位 FIF 账号 id·值从略>`）。签到那两发的 `id`、`studentId` 用它。 */
    val studentId: String,
    /** 登录名（14 位，`aiit` + 学号）。微课、作业列表那几发的 `userName` 用它。 */
    val userName: String,
    val schoolId: String,
    /** 32 位成员 id，从 FIF JWT 的第二段里解出来。课件/小组/课表那几发用它当 `userId`/`mid`/`studnetId`。 */
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

    /**
     * 有没有一把还没过期的 FIF token。
     *
     * 【取证 · ② 我今天自己发出去并看到的】10-20 上午拿真账号回放换来的校内 token 现打了一遍
     * 单点登录三跳（`lxtmp/adx_probe_fif.py`）：
     * ① 第一跳确实回 302，`Location` 里挂着 333 位的 JWT，payload 是
     *    `{realName, schoolId, iss:"fifac", id(19), exp, username:"aiit+学号", memberId(32)}`；
     * ② **第二跳 `studycenter-nh5/?token=…` 回 200，但一条 Set-Cookie 都没有** ——
     *    抓包清点里那句"11 个流文件扫过，没有任何一条响应下发过 SESSION"是真的；
     * ③ 后面 `getAiktUserIdByMemberId`、`termList`、`myClassroom`、签到两发、`getOneCourse`、
     *    `getFirstPageOfCourse` **全是在 cookie 罐为空的状态下打通的**，只要
     *    `authorization: Basic <jwt>` + `Visit-Type: mobile` 这两个头。
     *
     * 所以判有效只看 JWT 的 `exp`。上一版这里还要求"罐里得有一条 SESSION"，那是照官方
     * WebView 的样子抄的（它那个 SESSION 是前端自己写进 CookieManager 的，清点表也标了是推断），
     * 服务端既然不发，这条检查就永远不成立 —— 后果不是报错，是**每进一栏都重跑一遍完整 SSO**，
     * 白多三个来回。cookie 罐留着不删：官方那侧确实带，我们带着也无害。
     */
    suspend fun isSessionValid(): Boolean {
        val token = store.peekString(store.snapshot(), KEY_FIF_TOKEN).orEmpty()
        if (token.isBlank()) return false
        return jwtExpiresAt(token)?.let { System.currentTimeMillis() / 1000 + SKEW < it } ?: true
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
     * 把这一跳攒下来的 cookie 拼成一条 `name=value; name=value`，给 WebView 用。
     *
     * 为什么要这个：签到二维码解出来是个**网页**（`qrcodeV2/qrcodeHandler?token=…`），
     * 官方是拿带着 FIF 会话的 WebView 打开它、由服务端完成签到。我们 App 里那份会话
     * 只活在 OkHttp 的 cookie 罐里，WebView 看不到 —— 不搬过去，页面就是没登录的样子。
     */
    fun cookieHeader(host: String): String =
        synchronized(jar) {
            jar.entries.firstOrNull { it.key.endsWith(host) }?.value.orEmpty()
                .filter { it.expiresAt > System.currentTimeMillis() }
                .joinToString("; ") { "${it.name}=${it.value}" }
        }

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
     * 扫码签到：把码里那条链接**自己发一次**，读 302 的 `Location` 判结果。
     *
     * 为什么不是拿 WebView 打开（上一版是）：这一发根本不需要浏览器。它就是一个带着会话的 GET，
     * 服务端签完回一个 302，成没成全写在 `Location` 里。走 WebView 等于把会话交给 cookie 罐去碰运气 ——
     * 而这一层自己的注释就写着 FIF 服务端一条 Set-Cookie 都不发，罐子是空的；那一跳于是被推去登录，
     * 登录页又是微信专用的，屏幕上只剩「请在微信客户端打开链接」，签到压根没发生。
     *
     * 【取证 · 归属按本仓的三等同规矩标】
     * ③（目标 App 发出、被我们自己抓到的报文）：旧项目 2026-10-13 那一轮抓到的官方那一发 ——
     *   `GET /coursecenter-interaction/qrcodeV2/qrcodeHandler?token=…&openTheWay=2`，
     *   头是 `authorization: Basic <jwt>` + `Visit-Type: mobile` + 手机 WebView 那串 UA + `X-Requested-With`，
     *   回 302，`Location` 里分别见过 `signSuccess` / `codeExpired` / `loginManage` 这三种落点。
     * ②（我们发出去、看到回的）：**还没打过**。手上没有一张活码，装到机上真扫一次才算数 ——
     *   所以下面每一种落点都照原样把话说清楚，不许静默当成功。
     */
    suspend fun qrSign(raw: String): Result<String> {
        val trimmed = raw.trim()
        val token = qrToken(trimmed) ?: return Result.failure(IOException("码里没找到 token"))
        var attempt = qrSignOnce(qrUrl(trimmed, token))
            ?: return Result.failure(IOException("签到那一发没发出去（网络）"))
        if (attempt.first == 302 && attempt.second.contains("login", ignoreCase = true)) {
            // 会话过期：重跑一遍单点登录，只再发这一回。再不行就把原因交给人，不循环重试
            val back = performSso().getOrNull()
                ?: return Result.failure(IOException("AI 课堂的会话过期了，重登也没换到新会话"))
            saveIdentityCookies(bareJwt().orEmpty(), back)
            attempt = qrSignOnce(qrUrl(trimmed, token))
                ?: return Result.failure(IOException("重试那一发没发出去（网络）"))
        }
        return qrRead(attempt.first, attempt.second)
    }

    /** 码可能是完整 URL（带 `token=`），也可能整串就是 token。 */
    private fun qrToken(raw: String): String? {
        runCatching { raw.toHttpUrl().queryParameter("token") }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let { return it }
        Regex("[?&]token=([^&#]+)").find(raw)?.groupValues?.getOrNull(1)?.let { return it }
        return raw.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{20,}$")) }
    }

    /**
     * 拼上官方那一跳带的身份参数。
     *
     * `qrcodeHandler` 认的不只是 `token`：登录名、学号、appId 这几个都在 query 里带着，
     * 少带就被当成没登录的人扫了个码 —— 那正是被推去 `loginManage` 的那一类。
     * 码里已经有的参数一个都不覆盖（学校自己发的那种长链，参数比我们还全）。
     */
    private suspend fun qrUrl(raw: String, token: String): String {
        val base = if (raw.startsWith("http", ignoreCase = true)) {
            raw
        } else {
            "$HOST_STTP/coursecenter-interaction/qrcodeV2/qrcodeHandler?token=${urlEnc(token)}&openTheWay=2"
        }
        val inner = school.identity()
        val jwt = bareJwt().orEmpty()
        if (inner == null || jwt.isBlank()) return base
        return runCatching {
            val u = base.toHttpUrl()
            // `queryParameter` 是 HttpUrl 上的，Builder 没有 —— 先拿住原 URL 再判
            fun blank(k: String) = u.queryParameter(k).isNullOrBlank()
            u.newBuilder().apply {
                fun put(k: String, v: String) {
                    if (blank(k) && v.isNotBlank()) addQueryParameter(k, v)
                }
                put("access_token", jwt)
                put("_userCode", inner.userCode)
                put("code", inner.userCode)
                put("userCode", inner.userCode)
                put("_userName", inner.userName)
                put("_userType", inner.userType)
                put("appId", School.APP_ID)
                put("returnFromIscToAppFunc", "ReturnDefault")
            }.build().toString()
        }.getOrDefault(base)   // 码里那串不是合法 URL（少见）就别自己加工，照原样发
    }

    /**
     * 发那一发用的客户端：跟主那条一样，但**不带 cookie 罐**。
     *
     * 为什么单独开一个：OkHttp 的 `BridgeInterceptor` 在请求过一遍时会拿
     * `cookieJar.loadForRequest(url)` 的结果去**覆盖** `Cookie` 头。我们这一层的罐子
     * 存的键是 `https://sttp.fifedu.com`（`saveIdentityCookies` 写的），而 `loadForRequest`
     * 读的是 `sttp.fifedu.com` —— 对不上，所以现在它每次都回空、我手填那条侥幸活着。
     * 侥幸不是设计：签到这一发的 `id`/`studentId` 就是会话的一部分，
     * 不能靠另一个方法的键写错来保。这里把罐子摘掉，`Cookie` 头由 `qrCookies` 一个人说了算。
     * （罐子本身不动 —— 那会影响 FIF 另外十来发，那是另一件事，等能现打的时候再收。）
     */
    private val qrClient: OkHttpClient by lazy {
        client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
    }

    /** 发那一发，回 `HTTP 码 → Location`。网络本身挂了回 null。 */
    private suspend fun qrSignOnce(url: String): Pair<Int, String>? = withIo {
        val builder = Request.Builder().url(url).get()
            .header("User-Agent", WEB_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Upgrade-Insecure-Requests", "1")
            .header("X-Requested-With", "com.andaxin")
        authHeader()?.let { builder.header("authorization", it) }
        builder.header("Visit-Type", "mobile")
        qrCookies(url).takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        runCatching {
            qrClient.newCall(builder.build()).execute().use { it.code to (it.header("Location").orEmpty()) }
        }.getOrNull()
    }

    /**
     * 这一跳要带的 cookie：罐子里那些，加上身份那三条。
     *
     * 那三条（`id`/`studentId`/`currentUserName`）是**我们手动放进罐子的**（见 `saveIdentityCookies`）——
     * FIF 服务端自己一条都不发，等罐子攒出来是等不到的。
     */
    private suspend fun qrCookies(url: String): String {
        val merged = LinkedHashMap<String, String>()
        runCatching {
            val httpUrl = url.toHttpUrl()
            jar[httpUrl.host].orEmpty().filter { it.matches(httpUrl) }.forEach { merged[it.name] = it.value }
        }
        ids()?.let {
            if (it.studentId.isNotBlank()) {
                merged["id"] = it.studentId
                merged["studentId"] = it.studentId
            }
            if (it.userName.isNotBlank()) merged["currentUserName"] = urlEnc(it.userName)
        }
        return merged.entries.joinToString("; ") { (k, v) -> "$k=$v" }
    }

    /** 把 302 的落点翻译成人话。认不出的照实说认不出，别替学校宣布成功。 */
    private fun qrRead(code: Int, location: String): Result<String> {
        fun has(k: String) = location.contains(k, ignoreCase = true)
        return when {
            code != 302 -> Result.failure(IOException("签到那一发回了 HTTP $code（不是跳转），没签上"))
            has("signSuccess") -> Result.success("签到成功")
            has("codeExpired") -> Result.failure(IOException("这个码已经过期了，让老师重新开一张"))
            has("login") -> Result.failure(IOException("AI 课堂的会话失效了，重登一次也没签上"))
            has("course") -> Result.failure(IOException("没签上：这一跳被送去了课程页（码可能不是这堂课的）"))
            else -> Result.failure(IOException("没签上：跳转落点认不出来 ${location.take(70)}"))
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
        return withIo {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("资源中心回了 ${resp.code}")
                resp.body?.string().orEmpty()
            }
        }
    }

    /**
     * `adapt-app` 那一族用的 GET：**裸 JWT 放在 Authorization 里（不带 `Basic ` 前缀）**、
     * 带 `Referer: https://sttp.fifedu.com/adaptive/`、**不带 `Visit-Type`**。
     *
     * 【取证】③ 抓包里官方那三发（`courseDetail`/`newCourseChapterList`/`getUserDetail`）的请求头逐条读出来就是这个样子；
     * ② 我们照主站习惯发 `Basic <jwt>` 的时候，它回 `status:401` +
     * 「The string '…' doesn't have a valid JSON format」—— 它把 "Basic " 后那段当 base64 解，
     * 所以这一族要的是一串能被 base64 解开的东西，而裸 JWT 恰好能过（`lxtmp/_adapt_probe.txt`）。
     */
    suspend fun getAdapt(url: String, jwt: String, referer: String): JSONObject {
        val req = Request.Builder().url(url).get()
            .header("User-Agent", WEB_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Authorization", jwt)
            .header("Referer", referer)
            .header("X-Requested-With", "cn.edu.aiit.axx")
            .build()
        return withIo {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("adapt-app 回了 ${resp.code}")
                JSONObject(resp.body?.string().orEmpty())
            }
        }
    }

    private suspend fun get(url: String, header: String): String? = withIo {
        val req = Request.Builder().url(url).get().header("User-Agent", WEB_UA).build()
        client.newCall(req).execute().use { resp -> resp.header(header) }
    }

    private suspend fun getRaw(url: String) {
        withIo {
            val req = Request.Builder().url(url).get().header("User-Agent", WEB_UA).build()
            client.newCall(req).execute().use { it.body?.string() }
        }
    }

    suspend fun postForm(url: String, fields: List<Pair<String, String>>, auth: String?): String {
        val form = okhttp3.FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val builder = Request.Builder().url(url).post(form)
            .header("User-Agent", WEB_UA)
            .header("Accept", "application/json, text/plain, */*")
        if (auth != null) builder.header("authorization", auth)
        builder.header("Visit-Type", "mobile")
        val req = builder.build()
        return withIo {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("FIF 回了 ${resp.code}")
                resp.body?.string().orEmpty()
            }
        }
    }

    /** 带 JWT 发一发 GET，回根节点。 */
    suspend fun getJson(url: String, auth: String? = null): JSONObject {
        val builder = Request.Builder().url(url).get()
            .header("User-Agent", WEB_UA)
            .header("Accept", "application/json, text/plain, */*")
        builder.header("Visit-Type", "mobile")
        (auth ?: authHeader())?.let { builder.header("authorization", it) }
        val req = builder.build()
        return withIo {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("FIF 回了 ${resp.code}")
                JSONObject(resp.body?.string().orEmpty())
            }
        }
    }

    /**
     * 这一族每一发都得自己挪到 IO 线程上。
     *
     * 【取证 · ② 我们发出去并被现场撞到的】10-20 上午真机进 AI 课堂，界面直接把
     * `NetworkOnMainThreadException` 显示出来了 —— 上面这些用的是 `execute()` 同步调用，
     * 而调用方（`LaunchedEffect`）默认在主线程组合作用域里跑。`in.aiit` 那一族在
     * `School.post` 里统一包过一层，所以只有 FIF 这块漏了。别指望调用方记得包。
     */
    private suspend fun <T> withIo(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

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
