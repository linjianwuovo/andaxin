package com.andaxin.feature.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

/**
 * 更多功能那一格：只是入口，不取任何数据。
 * 顺序按"哪块证据厚"排，接报文时从上往下推。
 */
private val MORE = listOf(
    "报修" to "repair",
    "待办事务" to "affair",
    "AI 课堂" to "aiclass",
    "查寝签到" to "dorm",
    "考试成绩" to "exam",
    "素质学分" to "credit",
    "劳动教育" to "labor",
    "节假日离返校" to "holiday",
    "校园卡" to "card",
)

@Composable
fun MoreScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val ink = LocalInk.current
    ScaffoldPage(title = "更多功能", onBack = onBack) {
        Sheet {
            MORE.forEachIndexed { i, (label, route) ->
                Row(
                    Modifier.fillMaxWidth().clickable { open(route) }.padding(vertical = Gap.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, fontSize = 15.sp, color = ink.strong)
                    Text("›", fontSize = 15.sp, color = ink.ghost)
                }
                if (i != MORE.lastIndex) Hairline()
            }
        }
    }
}

/** 校园卡。17wanxiao 是第三方，要先用校方会话换它的会话，所以这一块接起来是两步。 */
@Composable
fun CampusCardScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val ink = LocalInk.current
    ScaffoldPage(title = "校园卡", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Palette.Blue, Radius.card)
                .padding(Gap.lg),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("安徽大学 · 校园卡", fontSize = 13.sp, color = Palette.Cream)
                Column {
                    Text("¥ —", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Palette.Cream)
                    Text("余额要等会话交换那一步接上", fontSize = 11.sp, color = Palette.Cream.copy(alpha = 0.7f))
                }
            }
        }
        Spacer(Modifier.height(Gap.lg))
        Sheet {
            Labeled(label = "最近一笔", value = "—")
            Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
            Labeled(label = "上月支出", value = "—")
        }
        Spacer(Modifier.height(Gap.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            Box(
                Modifier.weight(1f).height(46.dp).background(ink.hairline, Radius.field)
                    .clickable { open("card/flows") }.padding(Gap.md),
                contentAlignment = Alignment.Center,
            ) { Text("流水", fontSize = 14.sp, color = ink.body) }
            Box(
                Modifier.weight(1f).height(46.dp).background(ink.hairline, Radius.field)
                    .clickable { }.padding(Gap.md),
                contentAlignment = Alignment.Center,
            ) { Text("挂失", fontSize = 14.sp, color = ink.body) }
        }
        Spacer(Modifier.height(Gap.sm))
        Text(
            text = "挂失是写操作，接口没接之前这个按钮按了不动。",
            fontSize = 11.sp,
            color = ink.faint,
        )
        Spacer(Modifier.height(Gap.md))
        PlaceholderNote("挂失、充值这类写操作一律不代做：接上之后也只会把你送到最后一步。")
    }
}

/**
 * 登录。口令在本机用服务器的公钥 RSA 加密后直发 `getToken.do`，不经过任何第三方。
 *
 * 报文的形状是自己 10-19 打实样验过的，见 `data/capture/AuthFeed.kt` 顶上那段注释。
 */
@Composable
fun LoginScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ink = LocalInk.current
    var account by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // 被"续期失败"请回来的原因：进这一屏时按当前值取一次。
    // 别用 StateFlow + LaunchedEffect 往 note 里灌 —— 装机实测那样那一行根本不出来：
    // 会话翻转和 reason 落地不在同一帧，effect 跑到第二次时 reason 已经被消费成 null 了。
    var note by remember { mutableStateOf(graph.keepAlive.kicked.value) }
    DisposableEffect(Unit) {
        onDispose { graph.keepAlive.consumeKicked() }
    }

    ScaffoldPage(title = "登录", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(Gap.md)) {
            MiuixTextField(
                value = account,
                onValueChange = { account = it },
                label = "校园账号",
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            MiuixTextField(
                value = secret,
                onValueChange = { secret = it },
                label = "口令",
                useLabelAsPlaceholder = true,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Gap.lg))
        MiuixButton(
            onClick = {
                busy = true
                note = null
                scope.launch {
                    val r = graph.auth.login(account.trim(), secret)
                    busy = false
                    r.fold(
                        onSuccess = { graph.session.save(it); onDone() },
                        onFailure = { note = it.message ?: "登录失败" },
                    )
                }
            },
            enabled = !busy && account.isNotBlank() && secret.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(
                text = if (busy) "正在登录…" else "登录",
                fontSize = 16.sp, fontWeight = FontWeight.Medium,
            )
        }
        note?.let {
            Spacer(Modifier.height(Gap.md))
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(Gap.lg))
        Text(
            text = "先不登录，用占位数据看看界面",
            fontSize = 13.sp,
            color = ink.muted,
            modifier = Modifier.clickable {
                scope.launch { graph.session.browseWithoutLogin() }
                onBack()
            }.padding(vertical = Gap.sm),
        )
    }
}

/** 第一次打开的免责声明。文案是占位，真要对外得他自己写。 */
@Composable
fun OnboardingScreen(onAgree: () -> Unit) {
    val ink = LocalInk.current
    Column(
        Modifier.fillMaxSize().padding(Gap.xl),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Spacer(Modifier.height(Gap.xxl))
            Text("用之前说一句", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = ink.strong)
            Spacer(Modifier.height(Gap.lg))
            Text(
                text = "这个应用是给本校学生自己用的，数据直连学校接口，不经过任何第三方服务器；" +
                    "口令只存在你自己手机上。学校改了流程或者关掉某个入口，对应那一屏就会取不到数，" +
                    "这不是坏了，是学校那边变了。",
                fontSize = 14.sp,
                color = ink.body,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(Gap.md))
            Text(
                text = "还有：它不替你答题、不替你签到、不在后台刷任何进度。该你按的那一下，永远留给你按。",
                fontSize = 14.sp,
                color = ink.muted,
                lineHeight = 22.sp,
            )
        }
        Box(
            Modifier.fillMaxWidth().height(50.dp).background(MaterialTheme.colorScheme.primary, Radius.card)
                .clickable(onClick = onAgree),
            contentAlignment = Alignment.Center,
        ) {
            Text("知道了", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}


/** 校园卡流水：形状和别的列表一样，只是每行右边是金额。 */
@Composable
fun CardFlowsScreen(onBack: () -> Unit) {
    val ink = LocalInk.current
    ScaffoldPage(title = "校园卡流水", onBack = onBack) {
        listOf("10-18 12:04" to "-8.50", "10-17 07:31" to "-6.00", "10-15 18:22" to "+50.00").forEach { (when_, money) ->
            Sheet {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(when_, fontSize = 13.sp, color = ink.muted)
                    Text(money, fontSize = 14.sp, color = if (money.startsWith("-")) ink.body else Palette.Teal)
                }
            }
            Spacer(Modifier.height(Gap.sm))
        }
        PlaceholderNote("流水要等校园卡那套会话交换接上，现在这几行是占位。")
    }
}
