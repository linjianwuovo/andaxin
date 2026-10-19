package com.andaxin.data.capture

import com.andaxin.core.net.Http
import com.andaxin.core.store.SchoolIdentity
import com.andaxin.core.store.SessionStore
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * 校方业务接口的公共外壳：身份参数怎么带、表单怎么发、JSON 怎么钻。
 *
 * 证据：2026-10-19 20:47 米11 挂代理冷启动安小信（2.0.5 / versionCode 79）走到登录成功，
 * 抓到它自己发出的 `zhxy-new-scps` 那一圈 POST —— 每一条的表单里都带同一套身份字段：
 * `access_token` + `_userCode` + `userId` + `userCode` + `_userName` + `_userType` + `appId`。
 * `appId` 是 32 个 `1`，写死在客户端里，不是我们编的。
 *
 * 三个坑，都是这次实样里看见的：
 * ① `_userName` 有两种编码：原生那圈（okhttp UA）给一次 URL 编码的 `%E9%BB%84…`，
 *    另有几条（Dalvik UA）给两次编码的 `%25E9%25BB%2584…`。两种服务端都认（都回过 flag:true），
 *    这里统一按一次编码发 —— 少一层歧义，也免得有人以为名字里该带百分号。
 * ② 响应一律是 `{flag, code, msg, data}`，`data` 有时是对象、有时直接是数组、有时是分页壳
 *    （数组藏在 `data.data` 里）。只钻根节点会静默拿到空，界面看着像"没数据"。
 * ③ 服务端不给你 4xx：token 过期、参数缺了，都回 200 + `flag:false` + 一句中文 `msg`，
 *    或者干脆回一坨 HTML（我拿旧清单里那条 `selectStuSelfTimeTable.do` 试过来着，回的是登录页）。
 *    所以判成功只看 `flag` 和能不能钻到结构，别用 HTTP 状态码。
 */
class School(private val session: SessionStore) {

    private val _kicked = kotlinx.coroutines.flow.MutableStateFlow(false)

    /**
     * 「这把 token 已经被别的客户端顶掉了」的信号。
     *
     * 【取证】10-19 深夜实测：同一个账号，安小信一登录，我们这把 token 再打 news 那一组接口就回
     * `flag:false msg:"没有访问权限"`，而 `timeTable`、`msgCenter` 那几口还认它。
     * 换一把新 token 立刻全部恢复正常 —— 所以校方是**一个账号只留一个活会话**，
     * 不是权限没开，也不是我们参数写错。这种情况界面上一句"系统异常"是骗人的。
     */
    val kicked: kotlinx.coroutines.flow.StateFlow<Boolean> = _kicked

    fun consumeKicked() { _kicked.value = false }

    private val client = Http.client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 带身份表单发一次 POST，回根节点 JSON。没登录直接给失败，不发网络。 */
    suspend fun post(url: String, vararg fields: Pair<String, String>): Result<JSONObject> {
        val id = session.identity()
            ?: return Result.failure(Exception("还没登录，这一屏要校方数据"))
        return form(id, url, fields.toList())
    }

    private suspend fun form(id: SchoolIdentity, url: String, extra: List<Pair<String, String>>) =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder().apply {
                identityFields(id).forEach { (k, v) -> add(k, v) }
                extra.forEach { (k, v) -> add(k, v) }
            }.build()
            val request = Request.Builder().url(url).post(form)
                .header("Accept", "application/json")
                .header("User-Agent", AGENT)
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@use Result.failure(Exception("服务器回了 ${resp.code}"))
                    }
                    val root = runCatching { JSONObject(body) }.getOrNull()
                        ?: return@use Result.failure(Exception("回来的不是 JSON（多半是会话过期被踢到登录页）"))
                    if (!root.optBoolean("flag", false)) {
                        val msg = root.optString("msg").ifBlank { "校方说没通过" }
                        if (msg.contains("没有访问权限")) {
                            // 顶号是会话级的事，一次命中就该整体认，别每屏各报各的
                            _kicked.value = true
                            return@use Result.failure(Exception(KICKED))
                        }
                        return@use Result.failure(Exception(msg))
                    }
                    Result.success(root)
                }
            } catch (e: Exception) {
                Result.failure(Exception("连不上学校：${e.message ?: e.javaClass.simpleName}"))
            }
        }

    companion object {
        /** 被顶号时统一给这一句，界面上和到期那句要能分开。 */
        const val KICKED = "登录已在别处被顶掉（这台设备的服务端会话失效了），重新登一次。"

        /** 安小信自己那圈调用用的就是 okhttp 默认串，跟着它，别自造一个。 */
        const val AGENT = "okhttp/4.9.3"

        /** 32 个 1，抓包里每条都带。 */
        const val APP_ID = "11111111111111111111111111111111"

        const val MAIN = "https://in.aiit.edu.cn"
        const val SCPS = "$MAIN/zhxy-new-scps"
        const val INFO = "$MAIN/zhxy-information"

        /** 课表与成绩那一组在 csh 这个 context 下，前缀错了服务器直接回登录页 HTML。 */
        const val CSH = "$MAIN/zhxy-csh"

        /** 身份字段。`userId`/`userCode` 跟 `_userCode` 是同一个值，校方三条接口各认一个，全给。 */
        fun identityFields(id: SchoolIdentity): List<Pair<String, String>> = listOf(
            "access_token" to id.accessToken,
            "_userCode" to id.userCode,
            "userId" to id.userCode,
            "userCode" to id.userCode,
            "_userName" to enc(id.userName),
            "_userType" to id.userType,
            "appId" to APP_ID,
        )

        fun enc(s: String): String =
            if (s.isEmpty()) "" else runCatching { URLEncoder.encode(s, "UTF-8") }.getOrDefault(s)

        /** 分页壳：`data.data` 才是数组。拿不到就回空数组，不抛。 */
        fun page(root: JSONObject): JSONArray = root.optJSONObject("data")?.optJSONArray("data") ?: JSONArray()

        /** 有的接口 `data` 直接就是数组，有的挂在 `data` 里再套一层，两种都吃。 */
        fun array(root: JSONObject): JSONArray {
            val d = root.opt("data")
            return when (d) {
                is JSONArray -> d
                is JSONObject -> d.optJSONArray("data") ?: JSONArray()
                else -> JSONArray()
            }
        }
    }
}
