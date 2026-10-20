package com.andaxin.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.andaxin.shell.pressClick
import com.andaxin.BuildConfig
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.shell.Hairline
import com.andaxin.shell.Labeled
import com.andaxin.shell.Sheet
import com.andaxin.shell.SectionTitle
import kotlinx.coroutines.launch
import com.andaxin.shell.Chip
import com.andaxin.core.design.Type

@Composable
fun AboutScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Text("‹ 返回", fontSize = 14.sp, color = ink.muted, modifier = Modifier.pressClick(shrink = false, onClick = onBack).padding(vertical = Gap.sm))
        Spacer(Modifier.height(Gap.sm))
        Text("关于", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = ink.strong)

        Spacer(Modifier.height(Gap.xl))
        Sheet {
            Labeled(label = "名称", value = "安大信")
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            Labeled(label = "版本", value = BuildConfig.VERSION_NAME)
            Spacer(Modifier.height(Gap.sm))
            Hairline()
            Spacer(Modifier.height(Gap.sm))
            Labeled(label = "包名", value = BuildConfig.APPLICATION_ID)
        }

        Spacer(Modifier.height(Gap.xl))
        Spacer(Modifier.height(Gap.lg))
        Text(
            text = "边界：不代你提交申请、不代你付款、不后台刷任何进度 —— 写操作一律停在最后一步，由你按下去。",
            fontSize = Type.meta,
            lineHeight = Type.metaLine,
            color = ink.muted,
        )
        Spacer(Modifier.height(Gap.lg))


        VersionCard()
        Spacer(Modifier.height(Gap.xxl))
    }
}

/**
 * 官方安小信的版本对照。
 *
 * 这一条**不是"给我们自己检查更新"**，得把话说死：
 * `appInfo/checkVersion.do` 是校方给安小信用的那条通道，回的是**它的** apk。
 * 我们报上去的 `currentVersion` 是"我们抄协议照的那一版"的内部号（抓包里安小信自己报的就是内部号，
 * 不是 V2.0.5 那种名字 —— 真值见 `lxtmp/_shape6.txt`），于是 `news:true` 才真有意义：
 * **官方出了比我们照的更新的一版，协议可能变了**。
 * 装它那个 apk 这件事不做：这个 App 的存在就是为了不再回官方那个壳。
 * 我们自己的新版在 GitHub Releases，跟这条通道没关系。
 */
@Composable
private fun VersionCard() {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 本机存着"上一次官方报回来的内部号"，下次就拿它当对照基准；第一次进来用抓包抄来的 79
    var ref by remember { mutableStateOf(DEFAULT_REF) }
    var info by remember { mutableStateOf<com.andaxin.data.capture.OfficialVersion?>(null) }
    var why by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun query(at: String) {
        busy = true; why = null
        scope.launch {
            graph.home.officialVersion(at).fold(
                onSuccess = {
                    info = it
                    // 官方报回来的内部号存下：下次就照这一版对照
                    if (it.code.isNotBlank()) graph.store.setText(KEY_REF, it.code)
                },
                onFailure = { why = it.message ?: "没问到" },
            )
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        val p = graph.store.snapshot()
        ref = graph.store.peekString(p, KEY_REF).orEmpty().ifBlank { DEFAULT_REF }
        if (graph.session.signedIn()) query(ref)
        else why = "没登录，这条通道也要身份。"
    }

    Spacer(Modifier.height(Gap.xl))
    SectionTitle("官方安小信")
    Sheet {
        Labeled(
            label = "最新版本",
            value = when {
                busy -> "正在问…"
                info != null -> "${info!!.name.ifBlank { "—" }}（内部号 ${info!!.code.ifBlank { "—" }}）"
                else -> "还没问到"
            },
        )
        Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
        Labeled(label = "它给的更新说明", value = info?.note?.ifBlank { "没写" } ?: "—")
        Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
        Labeled(
            label = "比我们照的那版新吗",
            value = when {
                info == null -> "—"
                info!!.hasNew -> "是，官方到 ${info!!.name} 了，我们照的是 $ref"
                else -> "不是，还是 $ref 那一版"
            },
        )
        Spacer(Modifier.height(Gap.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
            Spacer(Modifier.weight(1f))
            Text(
                text = if (busy) "…" else "查一次",
                fontSize = 13.sp,
                color = if (busy) ink.ghost else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .pressClick(shrink = false, enabled = !busy) { query(ref) }
                    .padding(horizontal = Gap.md, vertical = Gap.sm),
            )
        }

        why?.let {
            Spacer(Modifier.height(Gap.sm))
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Gap.md))
        Text(
            "这是官方 App 自己的更新通道，我们只用它看官方升到哪儿了，不装它的包。",
            fontSize = 11.sp,
            color = ink.faint,
            lineHeight = 16.sp,
        )
    }
}

/** 本机存的对照基准：上一次官方报回来的内部号。 */
private const val KEY_REF = "school.officialRef"

/** 抓包里安小信自己报的那个内部号（`currentVersion=79`）。 */
private const val DEFAULT_REF = "79"
