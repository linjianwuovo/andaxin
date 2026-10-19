package com.andaxin.core.store

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 会话与首次使用状态。
 *
 * 只有形状，没有真实登录：登录接口那一侧要等我们自己抓一次才知道公钥是哪一把、
 * 口令按什么顺序加密。所以这里存的是"看没看过免责声明""是不是先不登录逛逛"，
 * 加一个账号字符串占位 —— 真 token 来了往 token 键里放，别往 account 里塞。
 */
@Immutable
data class Session(
    val onboarded: Boolean = false,
    val loggedIn: Boolean = false,
    val browsing: Boolean = false,
    val account: String = "",
    val userName: String = "",
    val dorm: String = "",
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

    /** 还剩不到两小时就算过期（学校给 8 小时） */
    suspend fun tokenExpired(): Boolean {
        val until = store.peekString(store.snapshot(), KEY_EXPIRES_AT)?.toLongOrNull() ?: return true
        return System.currentTimeMillis() / 1000 >= until - 7200
    }

    suspend fun agreeTerms() = store.setText(KEY_ONBOARDED, "1")

    /** 先不登录，用占位数据逛界面。真接上登录后这条路径就该被登录取代。 */
    suspend fun browseWithoutLogin() = store.setText(KEY_BROWSING, "1")

    suspend fun setDorm(value: String) = store.setText(KEY_DORM, value.ifBlank { null })

    suspend fun signOut() = store.setMany(
        Store.KEY_TOKEN to null,
        Store.KEY_USER_CODE to null,
        KEY_BROWSING to null,
    )

    private companion object {
        const val KEY_ONBOARDED = "app.onboarded"
        const val KEY_BROWSING = "app.browsing"
        const val KEY_DORM = "profile.dorm"
        const val KEY_REFRESH = "auth.refresh"
        const val KEY_USER_NAME = "auth.userName"
        const val KEY_USER_TYPE = "auth.userType"
        const val KEY_EXPIRES_AT = "auth.expiresAt"
    }
}
