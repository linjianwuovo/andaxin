package com.andaxin.feature.aiclass

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Type
import com.andaxin.feature.brief.ScaffoldPage
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField

/**
 * 六位签到码签到。
 *
 * 跟扫码那一屏是一对：那边扫二维码，这边输老师报的数字。真正发请求的是
 * `FifApi.signByCode`，那一发**零报文**（字段照官方 H5 抄的，见那个函数顶上的注释），
 * 所以这一屏只做三件事：把六位数收齐、发一次、把服务端的话原样回显。
 *
 * 不发第二次、不自动重试：签到是写操作，成没成由学校那边说了算，
 * 我们这边瞎重试只会让"到底签没签上"更难查。
 */
@Composable
fun SignCodeScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var ok by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    ScaffoldPage(title = "签到码", onBack = onBack) {
        Text(
            text = "老师报到的那 6 位数字，输进来就行。",
            fontSize = Type.meta,
            color = ink.muted,
            lineHeight = Type.metaLine,
        )
        Spacer(Modifier.height(Gap.lg))
        MiuixTextField(
            value = code,
            onValueChange = { raw -> code = raw.filter { it.isDigit() }.take(6); err = null; ok = null },
            label = "6 位签到码",
            useLabelAsPlaceholder = true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Gap.lg))
        MiuixButton(
            onClick = {
                busy = true
                err = null
                ok = null
                scope.launch {
                    graph.fifApi.signByCode(code).fold(
                        onSuccess = { ok = it },
                        onFailure = { err = it.message ?: "没签上" },
                    )
                    busy = false
                }
            },
            enabled = !busy && code.length == 6,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(
                text = if (busy) "正在签…" else "签到",
                fontSize = Type.title,
                fontWeight = FontWeight.Medium,
            )
        }
        ok?.let {
            Spacer(Modifier.height(Gap.lg))
            Text(
                it, fontSize = Type.body,
                color = if (ink.dark) Palette.NightTeal else Palette.TealInk,
                lineHeight = Type.bodyLine,
            )
        }
        err?.let {
            Spacer(Modifier.height(Gap.lg))
            Text(it, fontSize = Type.body, color = MaterialTheme.colorScheme.error, lineHeight = Type.bodyLine)
        }
        Spacer(Modifier.height(Gap.lg))
        Spacer(Modifier.height(Gap.md))
        Text(
            text = "学校那边得当场开着签到，这串码才作数；过点了再输会回一句失败。",
            fontSize = Type.micro,
            color = ink.faint,
            lineHeight = Type.microLine,
        )
        Spacer(Modifier.height(Gap.xxl))
    }
}
