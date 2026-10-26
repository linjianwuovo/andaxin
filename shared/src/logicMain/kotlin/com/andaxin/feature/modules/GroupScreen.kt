package com.andaxin.feature.modules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import com.andaxin.feature.brief.BriefEnv
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.data.capture.ClassGroup
import com.andaxin.data.capture.GroupKind
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.feature.brief.LoadWhy
import com.andaxin.shell.Hairline
import com.andaxin.shell.Sheet
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import com.andaxin.core.design.Type
import com.andaxin.shell.RowLine

/**
 * 班级群与班内名单。
 *
 * 两条接口都在 `zhxy-new-scps/im/` 上，请求侧除了身份七件套一个业务参数都不带
 * （取证见 `data/capture/CaptureIm.kt` 顶上那段）。
 *
 * 名单是**别人**的姓名和学号。这一屏的规矩：只在界面上看，不导出、不缓存到文件、不进日志 ——
 * 那不是我们的数据，只是学校让我们看见的。
 */
@Composable
fun GroupScreen(onBack: () -> Unit) {
    val graph = BriefEnv.graph
    val ink = LocalInk.current
    var kinds by remember { mutableStateOf<List<GroupKind>>(emptyList()) }
    var roster by remember { mutableStateOf<List<ClassGroup>>(emptyList()) }
    var why by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // 两发并发：群分类那条只是给个"有哪些群"，名单那条才是内容，谁先回来都不影响另一个
        val k = graph.im.kinds()
        val r = graph.im.roster()
        kinds = k.getOrDefault(emptyList())
        roster = r.getOrDefault(emptyList())
        why = r.exceptionOrNull()?.message ?: k.exceptionOrNull()?.message
        busy = false
    }

    ScaffoldPage(title = "班级群", onBack = onBack) {
        if (!busy && kinds.isNotEmpty()) {
            Sheet {
                kinds.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Gap.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 「班级群」这类是服务端给的分类名，下面挂的才是具体的班
                        Text(g.name.ifBlank { "一类群" }, fontSize = Type.body, color = ink.strong)
                        Text(
                            text = g.groups.joinToString("、") { it.name }.ifBlank { "没有具体的群" },
                            fontSize = Type.meta,
                            lineHeight = Type.metaLine,
                            color = ink.muted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Gap.lg))
        }

        val people = roster.flatMap { it.members }
        val filtered = if (query.isBlank()) people else people.filter {
            it.name.contains(query.trim()) || it.userCode.contains(query.trim())
        }

        if (people.isNotEmpty()) {
            MiuixTextField(
                value = query,
                onValueChange = { query = it },
                label = "按姓名或学号在本机筛一下",
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Gap.md))
            roster.forEach { g ->
                Text(
                    text = "${g.name} · ${g.memberCount} 人 · 名单里 ${filtered.size} 条",
                    fontSize = Type.meta,
                    fontWeight = FontWeight.Medium,
                    color = ink.muted,
                    modifier = Modifier.padding(bottom = Gap.sm, start = Gap.xxs),
                )
            }
            Sheet {
                filtered.forEachIndexed { i, m ->
                    RowLine(
                        title = m.name.ifBlank { "（校方没给名字）" },
                        // 这一栏的键在服务端叫 `xb`（拼音像"性别"），内容实测是学院名。
                        // 标签照内容写，不照键名写 —— 见 CaptureIm 顶上那段。
                        sub = m.college,
                        right = m.userCode,
                    )
                    if (i != filtered.lastIndex) Hairline()
                }
                if (filtered.isEmpty() && query.isNotBlank()) {
                    Text("本机筛不出这个人。", fontSize = Type.body, color = ink.faint, modifier = Modifier.padding(vertical = Gap.md))
                }
            }
        } else if (busy) {
            Text("正在拉…", fontSize = Type.meta, color = ink.faint)
        } else {
            // "没有名单。"是真问到了并且没有（灰），"为什么没问到"才交给 LoadWhy 分语气
            if (why != null) LoadWhy(why, BriefEnv.session) else Text("没有名单。", fontSize = Type.meta, color = ink.faint)
        }

        Spacer(Modifier.height(Gap.md))
    }
}
