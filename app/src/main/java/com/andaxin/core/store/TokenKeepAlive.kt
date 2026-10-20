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
    /** 「记住密码」那把 Keystore 密钥的持有者。没传（老构造）就等于这功能关着。 */
    private val vault: KeystoreVault? = null,
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
            kickedAck()
            // 被顶号是这一行最常撞到的事，而**校方一个账号只留一个活会话** ——
            // 记着口令的话就直接抢回来，别让人白跑一趟登录页。
            if (reloginFromVault()) return@withLock true
            session.signOut()
            _kicked.value = com.andaxin.data.capture.School.KICKED
            return@withLock false
        }
        if (!session.tokenExpired()) return@withLock true

        val refresh = session.refreshToken()
        val outcome = if (refresh.isNullOrBlank()) {
            Result.failure(Exception("本机没有可用的续期凭据"))
        } else {
            auth.refresh(refresh)
        }

        if (outcome.isSuccess) {
            session.save(outcome.getOrThrow())
            return@withLock true
        }
        // 续期那条路（`refresh.do`）服务端是死的，所以到期之后能不能自己活过来，
        // 全看本机有没有记住口令。有就重登一次，没有才请回登录页。
        if (reloginFromVault()) return@withLock true
        session.signOut()
        _kicked.value = "登录到期了，重新登一次。（${outcome.exceptionOrNull()?.message ?: "续期接口没给原因"}）"
        false
    }

    /**
     * 用本机记住的口令重新登一次。
     *
     * 这条路是**唯一**能自动续期的路：`mobile/refresh.do` 服务端回 404（现打验过，见 `AuthFeed`），
     * `refresh_token` 换不出东西。所以"记住密码"这件事的价值不在少打六个字，
     * 而在被顶号 / 到期之后 App 能自己活过来。
     *
     * 三条边界：① 没记口令、或者这版没接 Keystore → 直接 false，走原来的请回登录页；
     * ② 解不开（那把密钥被系统清了、或者密文坏了）→ 把废密文删掉再 false，别每次进来白试一遍；
     * ③ 重登本身失败（他在别处改了口令）→ 清登录态 + 把服务端原话带到登录页，**并且把口令忘掉**，
     *    免得每次过闸门都去撞一遍服务器。
     */
    private suspend fun reloginFromVault(): Boolean {
        val v = vault ?: return false
        val blob = session.passwordBlob() ?: return false
        val userCode = session.accountNow() ?: return false
        val password = v.open(blob) ?: run {
            session.forgetPassword()
            return false
        }
        // `kotlin.Result` 的 Success/Failure 两个子类是 internal，when 匹配不了，只能 getOrNull
        val fresh = auth.login(userCode, password).getOrNull()
        if (fresh != null) {
            // 先清后写：被顶掉那把旧 token 别在文件里过夜
            session.signOut(keepPassword = true)
            session.save(fresh)
            _kicked.value = null
            return true
        }
        // 登不进去＝口令在学校那边已经不作数了（他自己改过口令、或者账号被锁）。
        // 这时候必须把记的那份忘掉：留着它，每次过闸门都会去撞一遍服务器。
        session.forgetPassword()
        session.signOut()
        _kicked.value = "本机记的口令登不进去了，重新输一次。"
        return false
    }

    fun consumeKicked() {
        _kicked.value = null
    }
}
