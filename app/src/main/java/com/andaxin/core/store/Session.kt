package com.andaxin.core.store

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 会话与首次使用状态：全应用唯一一份登录态，落在 `andaxin` 那个 DataStore 文件里。
 *
 * `loggedIn` 是从"有没有 token"推出来的，不是另存的一个布尔 —— 免得出现
 * "token 已经清掉了但 loggedIn 还写着 true" 这种界面进不去、又 nowhere 可退的状态。
 * token 只存本机这一份，不进日志、不进仓、不进崩溃上报。
 *
 * 到点续期不在这里做，在 `TokenKeepAlive`：这一层只管存和读。
 */
@Immutable
data class Session(
    val onboarded: Boolean = false,
    val loggedIn: Boolean = false,
    val browsing: Boolean = false,
    val account: String = "",
    val userName: String = "",
    val dorm: String = "",
    /** 本机存了加密后的登录口令。能不能解开是另一回事：解不开时 `TokenKeepAlive` 会把它清掉并请人重输。 */
    val remembered: Boolean = false,
)

/**
 * 协议层要带的身份四件套。名字照校方字段来，别在中间层再翻一次命名。
 *
 * `userType` 是登录响应里那个 `userType`（学生是 "1"）。抓包里有一条消息列表打的是 "2"，
 * 那是安小信自己分系统消息时用的，不代表身份，别跟着改。
 */
@Immutable
data class SchoolIdentity(
    val accessToken: String,
    val userCode: String,
    val userName: String,
    val userType: String,
)

class SessionStore(private val store: Store) {

    val state: Flow<Session> = store.raw.map { p ->
        Session(
            onboarded = store.peekString(p, KEY_ONBOARDED) == "1",
            loggedIn = !store.peekString(p, Store.KEY_TOKEN).isNullOrBlank(),
            browsing = store.peekString(p, KEY_BROWSING) == "1",
            account = store.peekString(p, Store.KEY_USER_CODE).orEmpty(),
            userName = store.peekString(p, KEY_USER_NAME).orEmpty(),
            dorm = store.peekString(p, KEY_DORM).orEmpty(),
            remembered = !store.peekString(p, KEY_PW_BLOB).isNullOrBlank(),
        )
    }

    /** 登录成功后落盘。token 只存本机这一份文件里，不进日志、不进仓。 */
    suspend fun save(token: com.andaxin.data.capture.TokenBundle) {
        store.setMany(
            Store.KEY_TOKEN to token.accessToken,
            KEY_REFRESH to token.refreshToken,
            Store.KEY_USER_CODE to token.userCode,
            KEY_USER_NAME to token.userName,
            KEY_USER_TYPE to token.userType,
            KEY_EXPIRES_AT to (System.currentTimeMillis() / 1000 + token.expiresInSeconds).toString(),
            KEY_BROWSING to null,
        )
    }

    suspend fun refreshToken(): String? = store.peekString(store.snapshot(), KEY_REFRESH)

    /** 手里有没有 token。不读 Session 那条流，就是为了在协程里现查一次。 */
    suspend fun signedIn(): Boolean = !store.peekString(store.snapshot(), Store.KEY_TOKEN).isNullOrBlank()

    /**
     * 给协议层用的身份四件套。没登录给 null，让上层自己决定是退回登录页还是继续用占位数据。
     *
     * 校方那一圈 `.do` 每一条都要带这一套：`access_token` + `_userCode` + `userId` + `userCode` +
     * `_userName` + `_userType` + `appId`。少一个就回 `flag:false` 或者直接给你一坨 HTML。
     */
    suspend fun identity(): SchoolIdentity? {
        val p = store.snapshot()
        val token = store.peekString(p, Store.KEY_TOKEN)
        val code = store.peekString(p, Store.KEY_USER_CODE)
        if (token.isNullOrBlank() || code.isNullOrBlank()) return null
        return SchoolIdentity(
            accessToken = token,
            userCode = code,
            userName = store.peekString(p, KEY_USER_NAME).orEmpty(),
            userType = store.peekString(p, KEY_USER_TYPE) ?: "1",
        )
    }

    /** 还剩不到两小时就算过期（学校给 8 小时） */
    suspend fun tokenExpired(): Boolean {
        val until = store.peekString(store.snapshot(), KEY_EXPIRES_AT)?.toLongOrNull() ?: return true
        return System.currentTimeMillis() / 1000 >= until - 7200
    }

    suspend fun agreeTerms() = store.setText(KEY_ONBOARDED, "1")

    /** 先不登录，用占位数据逛界面。真接上登录后这条路径就该被登录取代。 */
    suspend fun browseWithoutLogin() = store.setText(KEY_BROWSING, "1")

    /** 从"先不登录逛逛"退回登录那一屏。只清浏览标志，不动 token。 */
    suspend fun leaveBrowsing() = store.setText(KEY_BROWSING, null)

    suspend fun setDorm(value: String) = store.setText(KEY_DORM, value.ifBlank { null })

    /** 记住口令：只存 Keystore 加密后的 base64，明文一刻都不落盘。 */
    suspend fun rememberPassword(blob: String?) = store.setText(KEY_PW_BLOB, blob)

    suspend fun passwordBlob(): String? = store.peekString(store.snapshot(), KEY_PW_BLOB)

    /** 重登要用的学号。`identity()` 要 token，被顶号那会儿不能用它，所以单开一条只读账号。 */
    suspend fun accountNow(): String? =
        store.peekString(store.snapshot(), Store.KEY_USER_CODE)?.takeIf { it.isNotBlank() }

    suspend fun forgetPassword() = store.setText(KEY_PW_BLOB, null)

    /**
     * 清登录态。
     *
     * `keepPassword` 只有一种情况该给 true：**被顶号 / 到期，而本机记着口令、马上要用它重登**。
     * 那时候清掉口令就等于把自己锁在门外（`refresh.do` 服务端是死的，重登只能靠口令）。
     * 他自己按「退出登录」走的是默认 false —— 人都要退了，不该继续存着钥匙。
     */
    suspend fun signOut(keepPassword: Boolean = false) = store.setMany(
        Store.KEY_TOKEN to null,
        Store.KEY_USER_CODE to null,
        KEY_REFRESH to null,
        KEY_USER_NAME to null,
        KEY_USER_TYPE to null,
        KEY_EXPIRES_AT to null,
        KEY_BROWSING to null,
        KEY_PW_BLOB to if (keepPassword) store.peekString(store.snapshot(), KEY_PW_BLOB) else null,
    )

    private companion object {
        const val KEY_ONBOARDED = "app.onboarded"
        const val KEY_BROWSING = "app.browsing"
        const val KEY_DORM = "profile.dorm"
        const val KEY_REFRESH = "auth.refresh"
        const val KEY_USER_NAME = "auth.userName"
        const val KEY_USER_TYPE = "auth.userType"
        const val KEY_EXPIRES_AT = "auth.expiresAt"
        const val KEY_PW_BLOB = "auth.pwBlob"
    }
}
