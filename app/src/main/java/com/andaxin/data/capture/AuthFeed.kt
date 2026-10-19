package com.andaxin.data.capture

import androidx.compose.runtime.Immutable
import com.andaxin.core.net.Crypto
import com.andaxin.core.net.Http
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/**
 * 登录与刷新 token。
 *
 * 形状来自两处独立取证，都不经过任何人的源码（详见 `core/net/Crypto.kt` 顶上那段）：
 * 17:12 抓到安小信自己发的 `POST mobile/getToken.do`，以及 16:47 本机按同一形状直发拿到的响应。
 *
 * 请求  POST https://in.aiit.edu.cn/zhxy-information/mobile/getToken.do
 *       form: userCode、password=RSA(公钥见 Crypto)/Base64(口令)
 * 响应  { "flag": true, "rows": [], "data": { "token": {
 *           access_token(36) / refresh_token(36) / expires_in=28800 /
 *           refresh_token_timeout=2592000 / userCode / userName / userType /
 *           fileAddress / access_pass_time / refresh_pass_time /
 *           passwordUpdated / phoneStatus } } }
 *
 * 两个坑：① token 藏在 `data.token` 二层，只钻根节点会静默拿到空；
 * ② 字段是 snake_case，别按驼峰找 `accessToken`，找不到就当登录失败。
 */

const val AUTH_BASE = "https://in.aiit.edu.cn/zhxy-information"

@Immutable
data class TokenBundle(
    val accessToken: String,
    val refreshToken: String,
    val userCode: String,
    val userName: String,
    val userType: String,
    val expiresInSeconds: Long,
)

class AuthFeed {

    private val client = Http.client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun login(userCode: String, password: String): Result<TokenBundle> =
        post("$AUTH_BASE/mobile/getToken.do", "userCode" to userCode, "password" to Crypto.encryptPassword(password))

    /** 过期前拿 refresh_token 换新的；换不动就退回登录页，别静默续。 */
    suspend fun refresh(refreshToken: String): Result<TokenBundle> =
        post("$AUTH_BASE/mobile/refresh.do", "refresh_token" to refreshToken)

    private suspend fun post(url: String, vararg fields: Pair<String, String>) = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        val request = Request.Builder().url(url).post(form)
            .header("Accept", "application/json")
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@use Result.failure(Exception("服务器回了 ${resp.code}"))
                }
                val root = runCatching { JSONObject(body) }.getOrNull()
                    ?: return@use Result.failure(Exception("回来的不是 JSON，可能被网关拦了"))
                if (!root.optBoolean("flag", false) && root.optString("msg").isNotBlank()) {
                    return@use Result.failure(Exception(root.getString("msg")))
                }
                // token 在 data.token 这一层，只钻根节点会静默拿到空串
                val token = root.optJSONObject("data")?.optJSONObject("token")
                    ?: return@use Result.failure(Exception("没拿到 token：回来的结构不对"))
                val access = token.optString("access_token")
                if (access.isBlank()) {
                    return@use Result.failure(Exception(
                        root.optString("msg").ifBlank { "账号或口令不对，或者要先改口令" }
                    ))
                }
                Result.success(
                    TokenBundle(
                        accessToken = access,
                        refreshToken = token.optString("refresh_token"),
                        userCode = token.optString("userCode"),
                        userName = token.optString("userName"),
                        userType = token.optString("userType", "1"),
                        expiresInSeconds = token.optLong("expires_in", 28800L),
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(Exception("连不上学校：${e.message ?: e.javaClass.simpleName}"))
        }
    }
}
