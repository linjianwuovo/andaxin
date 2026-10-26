package com.andaxin.feature.brief

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Type

/**
 * 正文里那张远程图：走 [PhotoHost.attached] 那根插槽，取不到就明写一句，不空一块灰地方。
 *
 * 为什么不直接借简报里那个 `AttachedPhoto`：那句失败文案是报修专用的
 * （"校方给的那个地址我们没实样，前缀是推的"），搬到公告正文里就是一句假话。
 * 所以这里只把"取—画—取不到说什么"这套动作共用，话由调用端给。
 *
 * 插槽没人插（比如某一段还没接远程解码）时 `attached` 是 null：那也算"取不到"，
 * 同样把这句摆出来，别什么都不画让人以为这一条本来就没图。
 */
@Composable
fun RemotePhoto(url: String, description: String, unavailable: String, modifier: Modifier = Modifier) {
    val ink = LocalInk.current
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var tried by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        bmp = PhotoHost.attached?.invoke(url)
        tried = true
    }
    val shown = bmp
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = description,
            contentScale = ContentScale.FillWidth,
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = Gap.md)
                .clip(Radius.tag)
                .aspectRatio(shown.width.toFloat() / shown.height.coerceAtLeast(1)),
        )
    } else {
        Text(
            text = if (tried) unavailable else "正在取这张图…",
            fontSize = Type.micro,
            color = if (tried) ink.muted else ink.faint,
            lineHeight = Type.metaLine,
            modifier = modifier.padding(bottom = Gap.md),
        )
    }
}
