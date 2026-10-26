package com.andaxin.core.store

import androidx.compose.runtime.Immutable
import com.andaxin.data.capture.AuthFeed
import com.andaxin.data.capture.AuthRejected
import com.andaxin.data.capture.School
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
            if (reloginFromVault().ok) return@withLock true
            dropSession(School.KICKED)
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
        val why = reloginFromVault()
        if (why.ok) return@withLock true
        dropSession(
            "登录到期了，重新登一次。（${outcome.exceptionOrNull()?.message ?: "续期接口没给原因"}）"
        )
        false
    }

    /**
     * 用本机记住的口令重新登一次。
     *
     * 这条路是**唯一**能自动续期的路：`mobile/refresh.do` 服务端是死的（现打验过，见 `AuthFeed`），
     * `refresh_token` 换不出东西。所以"记住密码"这件事的价值不在少打六个字，
     * 而在被顶号 / 到期之后 App 能自己活过来。
     *
     * 四条边界，每条都回一句自己的 [Relogin.why]：
     * ① 没记口令 / 没接 Keystore / 学号不在了 → 什么都不动，直接失败，走原来的请回登录页；
     * ② 解不开（那把密钥被系统清了、或者密文坏了）→ 把废密文删掉，别每次进来白试一遍；
     * ③ 学校**答了、并且说不行**（[AuthRejected]：他自己改了口令、账号被锁）→ 清登录态 +
     *    **把口令忘掉**，免得每次过闸门都去撞一遍服务器；
     * ④ 学校**没答上**（断网、网关给一坨 HTML）→ 什么都不清。那一次失败不说明口令错了，
     *    顺手擦掉就等于一次抖动永久废掉自动重登 —— 而这一层跑在手机上、没有日志，
     *    人第二天看到的只是"口令没了"。
     *
     * `why` 只写卡在哪一步，不含口令、不含密文、不含任何能拿去登录的东西。
     */
    private suspend fun reloginFromVault(): Relogin {
        val v = vault ?: return Relogin(false, "这版没接上系统密钥，记下的口令用不了。")
        val blob = session.passwordBlob() ?: return Relogin(false, "本机没留下口令。")
        val userCode = session.accountNow()
            ?: return Relogin(false, "口令还在，学号却不在了 —— 这一份救不回来，得重输一遍。")
        val password = v.open(blob) ?: run {
            session.forgetPassword()
            return Relogin(false, "系统那把密钥不在了（改过锁屏或指纹就会这样），记下的口令解不开。")
        }
        // `kotlin.Result` 的 Success/Failure 两个子类是 internal，when 匹配不了，只能 getOrNull
        val attempt = auth.login(userCode, password)
        val fresh = attempt.getOrNull()
        if (fresh != null) {
            /*
             * 只写一次：`save()` 一把 transaction 落全部七个键，token、学号、姓名、有效期
             * 没有一样是它不覆盖的 —— 所以旧代码在它前面那一发 `signOut(keepPassword = true)`
             * **一个字节都没多清掉，只多留了一个半状态的口子**：这两发之间协程被取消
             * （过闸门这一发跑在 `LaunchedEffect` 里，App 一进后台就取消），
             * 文件里就剩下"没 token、没学号、口令密文还在"这么一份解不开的孤儿。
             * 10-21 04:4x 从这台米11 上读回来的正是这一份：`auth.pwBlob` 有值、`auth.userCode` 这个键根本不存在。
             */
            session.save(fresh)
            _kicked.value = null
            return Relogin(true, "")
        }
        val err = attempt.exceptionOrNull()
        if (err is AuthRejected) {
            session.forgetPassword()
            session.signOut()
            return Relogin(false, "学校不认这份口令了：${err.message ?: "没给原因"}")
        }
        return Relogin(false, "这一发没问到学校，口令本机还留着，可以再按一次：${err?.message ?: "没给原因"}")
    }

    /**
     * 登录页上那颗「用这台机器记住的口令登进去」走的入口。
     *
     * 为什么要有这一颗：会话掉了（到期、被顶号、或者续期那一发没赶上）之后，
     * 本机明明还留着能登进去的口令，人却得重新输一遍 —— 那"记住密码"就只记住了个名。
     * 这里不判断"该不该自动登"：这是人按的一下，他自己选的，
     * 比在后台悄悄抢回会话稳妥（校方一个账号只留一个活会话，抢一下就把别的客户端挤下线）。
     */
    suspend fun reloginNow(): Relogin = mutex.withLock { reloginFromVault() }

    /**
     * 会话救不回来了：清登录态、把人请回登录页，并带上原因。
     *
     * `keepPassword` 给 true 是这一层的规矩 —— 掉线不等于口令错了，把口令连着会话一起擦掉，
     * 登录页上那颗一键重登的键就跟着没了。**只有他自己按「退出登录」才该清口令**（那一路走
     * `SessionStore.signOut()` 默认值）。已经确认被学校否掉的口令，`reloginFromVault()` 自己会忘。
     */
    private suspend fun dropSession(reason: String) {
        session.signOut(keepPassword = true)
        _kicked.value = reason
    }

    fun consumeKicked() {
        _kicked.value = null
    }
}

/**
 * 「用记住的口令重登一次」的结局。
 *
 * 单独一个类型而不是 `Boolean`，是因为这一句要在登录页上给人看：光说"失败了"没用于是
 * 每次都只能瞎猜。[why] 只在失败时有内容，成功时空着。
 */
@Immutable
data class Relogin(val ok: Boolean, val why: String)
