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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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

/**
 * 校园卡。17wanxiao 是第三方，要先用校方会话换它的会话，所以这一块接起来是三跳
 * （取证与坑见 `data/capture/CaptureCampusCard.kt` 顶上那段）。
 */
@Composable
fun CampusCardScreen(open: (String) -> Unit, onBack: () -> Unit) {
    val ink = LocalInk.current
    val (card, why) = rememberCard()
    ScaffoldPage(title = "校园卡", onBack = onBack) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Palette.Blue, Radius.card)
                .padding(Gap.lg),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("安徽信息工程学院 · 校园卡", fontSize = 13.sp, color = Palette.Cream)
                Column {
                    Text(
                        card?.let { "¥ %.2f".format(it.balance) } ?: "¥ —",
                        fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Palette.Cream,
                    )
                    Text(
                        text = when {
                            card != null -> "补助钱包 ¥ %.2f · ${card.statusText}".format(card.subsidy)
                            why != null -> "没拉到：" + why
                            else -> "正在换校园卡会话…"
                        },
                        fontSize = 11.sp, color = Palette.Cream.copy(alpha = 0.78f),
                    )
                }
            }
        }
        Spacer(Modifier.height(Gap.lg))
        Sheet {
            Labeled(
                label = "最近一笔",
                value = card?.flows?.firstOrNull()?.let {
                    "%s · %s".format(it.at.take(16), money(it)) } ?: "—",
            )
            Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
            Labeled(
                label = "今天支出",
                value = card?.let { "¥ %.2f".format(it.todayOut) } ?: "—",
            )
            Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
            Labeled(
                label = "近 30 天支出",
                value = card?.let { "¥ %.2f".format(it.windowOut) } ?: "—",
            )
            if (card != null && card.validUntil.isNotBlank()) {
                Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
                Labeled(label = "卡有效期", value = card.validUntil)
            }
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
        PlaceholderNote(
            "余额、有效期、流水都是校方那台新开普给的；「今天支出」「近 30 天支出」" +
                "是我们自己按流水加的 —— 服务端那四条统计口在这个租户上是坏的，没有现成的合计。",
        )
    }
}

/** 卡片数据只给校园卡两屏用，就地取，不再多开一个 Model。 */
@Composable
private fun rememberCard(): Pair<com.andaxin.data.capture.CampusCardClient.Card?, String?> {
    val graph = LocalAppGraph.current
    var card by remember { mutableStateOf<com.andaxin.data.capture.CampusCardClient.Card?>(null) }
    var why by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val r = graph.card.card()
        card = r.getOrNull()
        why = r.exceptionOrNull()?.message
    }
    return card to why
}

private fun money(f: com.andaxin.data.capture.CampusCardClient.Flow): String =
    (if (f.income) "+" else "-") + "%.2f".format(f.yuan) + (if (f.what.isBlank()) "" else " ${f.what}")

/**
 * 流水。服务端**不给合计、不给余额快照**，所以这里只列它给的行。
 * 分页键是 `count`（不是 pageSize）—— 少这一个键它只回总数不回行，见契约文件那条坑。
 */
@Composable
fun CardFlowsScreen(onBack: () -> Unit) {
    val ink = LocalInk.current
    val (card, why) = rememberCard()
    ScaffoldPage(title = "校园卡流水", onBack = onBack) {
        if (card == null) {
            Text(
                text = why ?: "正在换校园卡会话…",
                fontSize = 13.sp,
                color = if (why == null) ink.faint else MaterialTheme.colorScheme.error,
            )
        }
        card?.flows?.forEach { f ->
            Sheet {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(f.at.take(16), fontSize = 13.sp, color = ink.muted)
                        Text(
                            money(f), fontSize = 14.sp,
                            color = if (f.income) Palette.Teal else ink.body,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(Gap.xs))
                    Text(
                        text = listOf(f.what, f.term).filter { it.isNotBlank() }.joinToString(" · ")
                            .ifBlank { "（这一笔服务端没给说明）" },
                        fontSize = 11.sp, color = ink.faint,
                    )
                }
            }
            Spacer(Modifier.height(Gap.sm))
        }
        PlaceholderNote(
            if (card != null && card.flows.isEmpty())
                "近 30 天没有一笔消费记录（服务端回的是空行，不是没连上）。"
            else "这一屏是近 30 天的行，最多 200 条；消费点名字是校方给的，没做翻译。",
        )
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
    // 默认勾上：这台机器上"每次到期都要手打口令"才是问题，不记是例外
    var rememberPw by remember { mutableStateOf(true) }
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
        Spacer(Modifier.height(Gap.md))
        // 「记住密码」这一格不是方便少打六个字：校方那条 refresh.do 服务端是死的（404），
        // 续期只能拿口令重登一次。不记住 = 每 8 小时、以及每次被顶号，都得回到这一屏手打。
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("记住密码", fontSize = 14.sp, color = ink.strong)
                Text(
                    "口令用系统 Keystore 里一把不可导出的密钥加密后存本机；到期或被顶号时代你自动重登（会把对方客户端挤下线，校方一个账号只留一个活会话）。",
                    fontSize = 11.sp, color = ink.faint, lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.width(Gap.md))
            /*
             * 这颗勾**不用 Miuix 的 Switch**。装机回读发现"勾着没存上"，
             * 而两个分支都会写盘（不勾也要显式清掉），所以只要 `rememberPw` 是 true
             * 就一定有键 —— 键完全不存在，说明进来的那次 `rememberPw` 是 false。
             * 默认值是 true，中间只可能有一次我没预期的回调把它翻掉。
             * 与其去猜第三方组件在组合期会不会自己发一次 onCheckedChange，
             * 不如把这一格换成**只有我们自己那下 clickable 才改状态**的样子。
             */
            Box(
                Modifier
                    .size(30.dp)
                    .background(if (rememberPw) MaterialTheme.colorScheme.primary else ink.hairline, Radius.field)
                    .clickable { rememberPw = !rememberPw },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (rememberPw) "✓" else "",
                    fontSize = 16.sp, color = Palette.Cream, fontWeight = FontWeight.Medium,
                )
            }
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
                        onSuccess = {
                            /*
                             * **顺序不能反**：口令那两个键要先写，`session.save()` 放最后。
                             * `save()` 一落 token，外层 Gate 立刻把这一屏拆出组合，
                             * 而 `scope` 是 `rememberCoroutineScope()` —— 组合一走它的协程就取消，
                             * 排在后面的那两次 `setText` 一个都不会执行。
                             * 装机回读的表现就是：登录成功、首页全是真数据，
                             * 可「我的」那一格说"没记住"，而且连"存不下的原因"那个键都不存在
                             * （两个分支都没跑到，才是什么键都不留）。
                             */
                            if (rememberPw) {
                                val sealed = graph.vault.seal(secret)
                                graph.session.rememberPassword(sealed.getOrNull())
                                graph.session.rememberPasswordWhy(
                                    sealed.exceptionOrNull()?.let {
                                        "${it.javaClass.simpleName}: ${it.message?.take(90)}"
                                    },
                                )
                            } else {
                                graph.session.rememberPassword(null)
                                graph.session.rememberPasswordWhy(null)
                            }
                            graph.session.save(it)
                            onDone()
                        },
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


