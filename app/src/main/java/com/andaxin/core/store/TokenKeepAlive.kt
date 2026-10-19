package com.andaxin.core.store

import com.andaxin.data.capture.AuthFeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 手里这个 access_token 到点之前先换成新的；换不动就把人请回登录页，并且说清为什么。
 *
 * 为什么要有这一层：学校的 `expires_in` 是 28800 秒（8 小时，17:12 抓到安小信自己那条
 * `getToken.do` 时现读的数），而 `refresh_token` 给 30 天。没有这一层的话，早上登录、
 * 下午打开 App 就是一个看不出原因的"哪一屏都没数据"，用户只会以为是我们坏了。
 *
 * 判据只有两个：已登录、且剩余不到两小时（`SessionStore.tokenExpired()`）。所以启动时、
 * 每次回到前台时、以及以后每一个要带 token 出网的请求之前，都可以无脑调一次 —— 新鲜的时候
 * 它一个字节都不发。
 *
 * 加锁是因为"回到前台"和"轮询到点"可能同一秒各调一次：两次 refresh 打出去，
 * 后一次会把前一次刚存的 refresh_token 覆盖掉，而学校那条 refresh_token 是一次性的话，
 * 我们手上就只剩一个作废的。宁可串行等那一个来回。
 *
 * **现在这一层实际只跑"请回登录页"那半边**：`AuthFeed.refresh` 顶上记着，10-19 拿真
 * refresh_token 直发那条接口回的是 404。所以到期之后用户会看到一句写明原因的提示、
 * 重登一次，而不是一个说不清的空白首页。接口哪天回去了，这一层不用动。
 */
class TokenKeepAlive(
    private val auth: AuthFeed,
    private val session: SessionStore,
    /** 由 AppGraph 把 `School.kicked` 递进来；这一层不去 new 协议层的东西。 */
    private val kickedDetector: kotlinx.coroutines.flow.StateFlow<Boolean>? = null,
    private val kickedAck: () -> Unit = {},
) {

    private val mutex = Mutex()

    private val _kicked = MutableStateFlow<String?>(null)

    /** 被请回登录页的原因。界面显示过一次就该调 [consumeKicked] 抹掉，免得下次进来还挂着。 */
    val kicked: StateFlow<String?> = _kicked.asStateFlow()

    /**
     * @return true = 现在手里这个 token 还能用（本来就新鲜，或者刚续上了）。
     * 没登录也算 true：这一层不为"没登录"负责，那是 `Gate` 的事。
     */
    suspend fun ensureFresh(): Boolean = mutex.withLock {
        if (!session.signedIn()) return@withLock true
        // 被顶号比过期更常见：任何一屏先撞到"没有访问权限"，这里下一次过闸门时就直接请回登录页，
        // 不再让每一屏各自挂一句"系统异常"。
        if (kickedDetector?.value == true) {
            session.signOut()
            _kicked.value = com.andaxin.data.capture.School.KICKED
            kickedAck()
            return@withLock false
        }
        if (!session.tokenExpired()) return@withLock true

        val refresh = session.refreshToken()
        val outcome = if (refresh.isNullOrBlank()) {
            Result.failure(Exception("本机没有可用的续期凭据"))
        } else {
            auth.refresh(refresh)
        }

        outcome.fold(
            onSuccess = { session.save(it); true },
            onFailure = { e ->
                session.signOut()
                _kicked.value = "登录到期了，重新登一次。（${e.message ?: "续期接口没给原因"}）"
                false
            },
        )
    }

    fun consumeKicked() {
        _kicked.value = null
    }
}
