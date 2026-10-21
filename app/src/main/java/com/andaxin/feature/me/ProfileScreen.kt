package com.andaxin.feature.me

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Type
import com.andaxin.core.store.AvatarStore
import com.andaxin.shell.Labeled
import com.andaxin.shell.LoadWhy
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.shell.Sheet
import com.andaxin.shell.pressClick
import kotlinx.coroutines.launch

/**
 * 个人信息。点头像进来这一屏，里面是：
 * 头像（点一下从相册换）→ 姓名 / 学号 → 校方那两条只读接口给的身份信息 → 退出登录。
 *
 * 为什么把「退出登录」搬到这里：它原来躺在「我的」最底下，跟主题、关于混在一起；
 * 而退出是账号级动作，放进"这个人的资料"里才讲得通（他 10-21 09:3x 的说法）。
 *
 * 头像**只在本机生效** —— 校方没有改头像的接口（`getPersonalInfo.do` 只读），
 * 所以下面那行小字要写明这一点，不能让人以为换到学校那边去了。
 */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val graph = LocalAppGraph.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ink = LocalInk.current
    val session by graph.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())

    var profile by remember { mutableStateOf<com.andaxin.data.capture.Profile?>(null) }
    var why by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var armedToQuit by remember { mutableStateOf(false) }
    var avatarNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(session.generation, session.loggedIn) {
        loading = true
        why = null
        if (session.loggedIn) {
            graph.profile.load()
                .onSuccess { profile = it }
                .onFailure { why = it.message ?: "资料没拉到" }
        }
        loading = false
    }

    // 头像不是 Compose 状态，版本号变了就重新解一次
    LaunchedEffect(Unit) { AvatarStore.refresh(ctx) }
    val avatarVersion by AvatarStore.version.collectAsStateWithLifecycle()
    val avatar = remember(avatarVersion) { AvatarStore.load(ctx) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            avatarNote = if (AvatarStore.save(ctx, uri)) "换好了，只在本机生效。" else "这张图没读进来，换一张试试。"
        }
    }

    ScaffoldPage(title = "个人信息", onBack = onBack) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(Gap.lg))
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Palette.Sand, Radius.pill)
                    .pressClick {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (avatar != null) {
                    Image(
                        bitmap = avatar.asImageBitmap(),
                        contentDescription = "头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        text = session.userName.firstOrNull()?.toString() ?: "安",
                        fontSize = Type.display,
                        fontWeight = FontWeight.Medium,
                        color = if (session.loggedIn) ink.strong else ink.muted,
                    )
                }
            }
            /*
             * 头像下面原来挂着一行蓝字「点一下换头像」，他 10-2x 说太碍眼，去掉。
             * 头像本身还是可点的（点开相册），只是不再拿一行字教人点它。
             */
            avatarNote?.let {
                Spacer(Modifier.height(Gap.xs))
                Text(it, fontSize = Type.micro, color = ink.faint)
            }

            Spacer(Modifier.height(Gap.lg))
            Text(
                text = session.userName.ifBlank { if (session.loggedIn) session.account else "未登录" },
                fontSize = Type.display,
                fontWeight = FontWeight.SemiBold,
                color = ink.strong,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (session.account.isBlank()) "校园账号" else "校园账号 ${session.account}",
                fontSize = Type.meta,
                color = ink.muted,
            )
            Spacer(Modifier.height(Gap.lg))

            if (!session.loggedIn) {
                LoadWhy("登录后才看得到学籍资料。", wide = false)
            } else {
                val p = profile
                if (p != null) {
                    Sheet {
                        val rows = listOf(
                            "学院" to p.college,
                            "专业" to p.major,
                            "班级" to p.className,
                            "学制" to p.length,
                            "学籍状态" to p.status,
                            "入学年月" to p.enrolledAt,
                            "辅导员" to p.counselor,
                            "辅导员电话" to p.counselorPhone,
                            "手机" to p.mobile,
                            "昵称" to p.nickname,
                        ).filter { it.second.isNotBlank() }
                        rows.forEachIndexed { i, (label, value) ->
                            Labeled(label = label, value = value)
                            if (i != rows.lastIndex) {
                                Spacer(Modifier.height(Gap.xs))
                                com.andaxin.shell.Hairline()
                                Spacer(Modifier.height(Gap.xs))
                            }
                        }
                    }
                } else if (loading) {
                    Text("正在拉资料…", fontSize = Type.meta, color = ink.faint)
                } else {
                    LoadWhy(why ?: "资料没拉到。")
                }
            }

            Spacer(Modifier.height(Gap.xl))

            if (session.loggedIn) {
                /*
                 * 退出登录：一颗圆角矩形按钮，字居中，就写"退出登录"（他 10-21 09:3x：
                 * "直接写退出登录、用圆矩框"）。连按两次才真退 —— 不留神碰到就掉登录太亏。
                 */
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(ink.offFill, Radius.field)
                        .pressClick {
                            if (armedToQuit) {
                                scope.launch { graph.session.signOut() }
                                armedToQuit = false
                            } else {
                                armedToQuit = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (armedToQuit) "再点一次就退出" else "退出登录",
                        fontSize = Type.body,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(Gap.xxl))
        }
    }
}
