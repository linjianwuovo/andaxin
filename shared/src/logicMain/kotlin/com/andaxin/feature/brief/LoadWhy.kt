package com.andaxin.feature.brief

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Type
import com.andaxin.core.store.Session
import com.andaxin.core.store.SessionStore
import com.andaxin.shell.pressClick
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults

/*
 * 从 app/shell/Bits.kt 搬进共享层的那一份（安卓那份现在只是个转发壳，签名没变，
 * RootShell 那些调用点一行不用改）。搬的原因：BriefKit 那套列表/详情/表单屏要用它，
 * 而 BriefKit 要进共享层 —— 原版里那句 `LocalAppGraph.current` 在共享层不存在，
 * 所以把"要不要显示那颗去登录"所依赖的东西降级成一个参数：只认 SessionStore。
 *
 * 语义照原样：登录态没了才把这句失败说成"要登录"，否则才是真出错。
 * 安卓那份注释里的话仍然成立 —— 手机上没有第二种解释。
 */

/** 拉不到东西时那一句：说清为什么，顺手给一颗能按的"去登录"（只在确实没登录时给）。 */
@Composable
fun LoadWhy(why: String?, session: SessionStore, modifier: Modifier = Modifier, wide: Boolean = true) {
    if (why.isNullOrBlank()) return
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    val state by session.state.collectAsState(Session())
    val needsLogin = !state.loggedIn
    Column(modifier) {
        Text(
            text = why,
            fontSize = Type.meta,
            lineHeight = Type.metaLine,
            color = if (needsLogin) ink.muted else MaterialTheme.colorScheme.error,
        )
        if (needsLogin) {
            Spacer(Modifier.height(if (wide) Gap.lg else Gap.sm))
            if (wide) {
                MiuixButton(
                    onClick = { scope.launch { session.leaveBrowsing() } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("去登录", fontSize = Type.title, fontWeight = FontWeight.Medium)
                }
            } else {
                Text(
                    text = "去登录",
                    fontSize = Type.meta,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.pressClick { scope.launch { session.leaveBrowsing() } },
                )
            }
        }
    }
}
