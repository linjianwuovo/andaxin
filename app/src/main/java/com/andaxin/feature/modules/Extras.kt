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
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet

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
fun CampusCardScreen(onBack: () -> Unit) {
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
                    .clickable { }.padding(Gap.md),
                contentAlignment = Alignment.Center,
            ) { Text("流水", fontSize = 14.sp, color = ink.body) }
            Box(
                Modifier.weight(1f).height(46.dp).background(ink.hairline, Radius.field)
                    .clickable { }.padding(Gap.md),
                contentAlignment = Alignment.Center,
            ) { Text("挂失", fontSize = 14.sp, color = ink.body) }
        }
        Spacer(Modifier.height(Gap.md))
        PlaceholderNote("挂失、充值这类写操作一律不代做：接上之后也只会把你送到最后一步。")
    }
}

/** 登录。口令要 RSA 之后才发出去，公钥和参数得我们自己抓一次才算数。 */
@Composable
fun LoginScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val ink = LocalInk.current
    var account by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }

    ScaffoldPage(title = "登录", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(Gap.md)) {
            TextField(
                value = account,
                onValueChange = { account = it },
                placeholder = { Text("校园账号", fontSize = 14.sp, color = ink.ghost) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = secret,
                onValueChange = { secret = it },
                placeholder = { Text("口令", fontSize = 14.sp, color = ink.ghost) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Gap.lg))
        Box(
            Modifier.fillMaxWidth().height(50.dp)
                .background(MaterialTheme.colorScheme.primary, Radius.card)
                .clickable { note = "登录接口还没接：口令怎么加密、公钥是哪一把，都要我们自己抓一次才写。" },
            contentAlignment = Alignment.Center,
        ) {
            Text("登录", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
        }
        note?.let {
            Spacer(Modifier.height(Gap.md))
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, lineHeight = 18.sp)
        }
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
