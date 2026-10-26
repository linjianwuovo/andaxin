package com.andaxin.feature.modules

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andaxin.core.concurrency.parsePatternTime
import com.andaxin.core.concurrency.todayLocalDate
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Type
import com.andaxin.core.location.Fix
import com.andaxin.core.location.SportHost
import com.andaxin.core.map.Geo
import com.andaxin.data.capture.DormTaskInfo
import com.andaxin.feature.brief.BriefEnv
import com.andaxin.feature.brief.ScaffoldPage
import com.andaxin.feature.sport.latLng5
import com.andaxin.shell.Hairline
import com.andaxin.shell.RowLine
import com.andaxin.shell.Sheet
import com.andaxin.shell.enterFade
import com.andaxin.shell.pressClick
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults

/*
 * 一场查寝/晚点名的详情与签到 —— 10-26 深夜从 `app/.../DormTaskScreen.kt` 搬进来，
 * 安卓那一扇现在推的就是这一份，iOS 靠 `dorm/task/{id}` 落地。
 *
 * 这一屏的写操作只有一处：用户自己按「提交签到」。照片先走
 * `app/file/uploadFileToFastdfs`（multipart 的 `file`），再把 `data.path` 填进
 * `app/dorm/signIn` 的 `signinPhoto`；定位一律 BD-09，`latLng` 是「经度,纬度」。
 * 这套字段是他旧项目里已经跑通的那一套（`CheckinRepository.submitSignIn`），
 * 这里只换成新仓的网络壳和排版，别另发明一套键名。
 *
 * 不自动签、不代点：进屏只读到详情和当前位置，最后那一下留给人。
 *
 * 搬动时换掉的只有三样平台东西，别的逐字照旧：
 * ① 相机 —— 原来 `TakePicture` + `FileProvider` + `File`，现在 `BriefEnv.takePhoto`
 *    那根插槽（安卓在 `AndroidPhotoHost.install` 里插，iOS 在 `IosCamera.install` 里插），
 *    交回的是**字节 + 文件名 + 一张能直接画的预览**，这一屏不再碰文件；
 * ② 定位权限 —— 原来 `RequestMultiplePermissions` 的 launcher，现在 `SportHost.askLocation`
 *    （运动那几屏先用的同一根缝）；
 * ③ 时间 —— `java.time` 换 kotlinx-datetime，`String.format("%.5f")` 换 `latLng5`
 *    （五位是小数点后五位，差一位就是差十米，别拿 f2 凑）。
 */

/** 拍到的那一张：字节递给 `uploadPhoto`，名字是 multipart 那一栏的文件名，预览只用来画。 */
private class Shot(val bytes: ByteArray, val name: String, val preview: ImageBitmap?)

@Composable
fun DormTaskScreen(dateId: String, onBack: () -> Unit) {
    val graph = BriefEnv.graph
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    val base = remember(dateId) { graph.dormBoard.lastTasks[dateId] }

    var info by remember { mutableStateOf<DormTaskInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var why by remember { mutableStateOf<String?>(null) }

    var fix by remember { mutableStateOf<Fix?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locWhy by remember { mutableStateOf<String?>(null) }

    var photo by remember { mutableStateOf<Shot?>(null) }

    var submitting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var noteBad by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        why = null
        graph.dormBoard.taskInfo(dateId).fold(
            onSuccess = { info = it; loading = false },
            onFailure = { why = it.message ?: "签到详情没拉到"; loading = false },
        )
    }

    fun locate() {
        if (!graph.gps.available && fix == null) {
            locWhy = "没给定位权限"
            return
        }
        scope.launch {
            locating = true
            locWhy = null
            // 先给上次已知点垫着（旧项目也是先取 lastKnown），再等 8 秒换更准的一点
            graph.gps.lastKnown()?.let { known ->
                if (fix == null) fix = Fix(known, 0.0, 0f, true)
            }
            val fresh = withTimeoutOrNull(8_000) {
                runCatching { graph.gps.fixes().first() }.getOrNull()
            }
            if (fresh != null) fix = fresh
            if (fix == null) locWhy = "没拿到定位。到窗边或楼下再试一次。"
            locating = false
        }
    }

    // 要权限那个动作：宿主没插（不该发生）就直接说明，不静默什么都不干
    val askLoc: (() -> Unit)? = SportHost.askLocation?.invoke { granted ->
        if (granted) locate() else locWhy = "没给定位权限，签到这一步需要它"
    }
    fun askOrLocate() {
        if (graph.gps.available) locate() else askLoc?.invoke() ?: run { locWhy = "没给定位权限" }
    }

    // 拍照：插槽交回的是"按一下就开相机"那个动作；没插就等于这一端开不动相机，明说一句。
    // 空字节当"没拍到"处理 —— 递一个 0 字节的文件上去，学校那头只会回一句看不懂的话。
    val shoot: (() -> Unit)? = BriefEnv.takePhoto?.invoke { bytes, name, preview ->
        if (bytes.isEmpty() || preview == null) {
            photo = null
            note = "照片读不出来，重拍一张"
            noteBad = true
        } else {
            photo = Shot(bytes, name, preview)
            note = null
            noteBad = false
        }
    }

    fun submit() {
        val d = info ?: return
        val f = fix ?: return
        if (d.needPhoto && photo == null) {
            note = "这一场要照片，先拍一张"
            noteBad = true
            return
        }
        val bd = Geo.wgsToBd(f.where.latitude, f.where.longitude)
        scope.launch {
            submitting = true
            note = null
            var photoUrl = ""
            if (d.needPhoto) {
                val shot = photo
                if (shot == null) {
                    submitting = false
                    return@launch
                }
                graph.dormBoard.uploadPhoto(shot.bytes, shot.name).fold(
                    onSuccess = { photoUrl = it },
                    onFailure = {
                        submitting = false
                        note = it.message ?: "照片没传上去"
                        noteBad = true
                        return@launch
                    },
                )
            }
            graph.dormBoard.submitSignIn(
                dateId = d.dateId,
                photoUrl = photoUrl,
                place = d.placeName,
                bdLng = bd.longitude,
                bdLat = bd.latitude,
            ).fold(
                onSuccess = {
                    submitting = false
                    submitted = true
                    note = "签到成功"
                    noteBad = false
                    /*
                     * 不能只把本地那份 copy(signed = true)：签到时间是服务端这发提交之后才写上的，
                     * 不重拉详情就永远是 null（10-21 晚装机看到的那个"签到时间 null"就是这一步漏了）。
                     * load() 失败也不回退成功态 —— 签到本身已经过了。
                     */
                    load()
                },
                onFailure = {
                    submitting = false
                    note = it.message ?: "签到没通过"
                    noteBad = true
                },
            )
        }
    }

    LaunchedEffect(dateId) { load() }
    LaunchedEffect(Unit) { askOrLocate() }

    ScaffoldPage(title = "签到", onBack = onBack, onRefresh = { load() }) {
        val d = info
        if (d == null) {
            if (base != null) {
                Sheet(modifier = Modifier.enterFade()) {
                    Text(base.title.ifBlank { "签到任务" }, fontSize = Type.title, fontWeight = FontWeight.SemiBold, color = ink.strong)
                    Spacer(Modifier.height(Gap.xs))
                    Text("${base.date}  ${base.startAt} ~ ${base.endAt}", fontSize = Type.meta, color = ink.faint)
                }
                Spacer(Modifier.height(Gap.md))
            }
            if (loading) {
                Text("正在拉签到详情…", fontSize = Type.body, color = ink.faint)
            } else {
                Text(why ?: "签到详情没拉到", fontSize = Type.body, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(Gap.md))
                MiuixButton(
                    onClick = { scope.launch { load() } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("重试", fontSize = Type.title, fontWeight = FontWeight.Medium) }
            }
            Spacer(Modifier.height(Gap.xxl))
            return@ScaffoldPage
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val start = parseAt(d.date, d.startAt)
        val end = parseAt(d.date, d.endAt)
        val sameDay = d.date == todayLocalDate().toString()
        val before = start != null && now < start
        val after = end != null && now > end
        val open = !d.signed && d.signinMode == "1" && sameDay &&
            (if (start != null && end != null) !before && !after else d.status == "0")
        val phase = when {
            d.signed || submitted -> "已签到"
            d.signinMode == "2" -> "扫码签到"
            !sameDay -> "这不是今天的签到任务"
            before -> "还没到签到时间"
            after -> "签到已结束"
            else -> "签到进行中"
        }
        val good = if (ink.dark) Palette.NightTeal else Palette.TealInk

        Sheet(modifier = Modifier.enterFade()) {
            Text(
                text = d.title.ifBlank { "签到任务" },
                fontSize = Type.title,
                fontWeight = FontWeight.SemiBold,
                color = ink.strong,
            )
            Spacer(Modifier.height(Gap.xs))
            Text(
                text = listOf(d.date, "${d.startAt} ~ ${d.endAt}").filter { it.isNotBlank() }.joinToString("  "),
                fontSize = Type.meta,
                color = ink.faint,
            )
            Spacer(Modifier.height(Gap.sm))
            Text(
                text = phase,
                fontSize = Type.body,
                fontWeight = FontWeight.Medium,
                color = if (d.signed || submitted) good else ink.muted,
            )
            if (d.signed || submitted) {
                if (d.signinAt.isNotBlank()) {
                    Text("签到时间 ${d.signinAt}", fontSize = Type.micro, color = ink.faint)
                }
                if (d.photoName.isNotBlank()) {
                    Text("签到照片已存档", fontSize = Type.micro, color = ink.faint)
                }
            }
        }

        Spacer(Modifier.height(Gap.md))
        Sheet {
            RowLine("签到方式", sub = if (d.signinMode == "2") "扫码签到" else "定位签到")
            Hairline()
            if (d.placeName.isNotBlank()) {
                RowLine("签到地点", sub = d.placeName)
                Hairline()
            }
            if (d.locationRange.isNotBlank()) {
                RowLine("签到范围", sub = withMeter(d.locationRange))
                Hairline()
            }
            if (d.lateReturn.isNotBlank()) {
                RowLine("晚归阈值", sub = "${d.lateReturn} 分钟")
                Hairline()
            }
            RowLine(
                "签到照片",
                sub = if (d.needPhoto) "要" else "不要",
                right = if (d.signed) "已存档" else "",
            )
            if (d.requirement.isNotBlank()) {
                Hairline()
                RowLine("签到要求", sub = d.requirement)
            }
            if (d.creator.isNotBlank() || d.unit.isNotBlank()) {
                Hairline()
                RowLine(
                    "发起",
                    sub = listOf(d.creator, d.unit).filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
        }

        if (d.signed || submitted) {
            Spacer(Modifier.height(Gap.lg))
            Spacer(Modifier.height(Gap.xxl))
            return@ScaffoldPage
        }

        if (d.signinMode == "2") {
            Spacer(Modifier.height(Gap.md))
            Sheet {
                Text("这一场是扫码签到。", fontSize = Type.body, color = ink.muted)
                Spacer(Modifier.height(Gap.xs))
                Text("扫码那一发还没接，暂时只能在学校 App 里扫。", fontSize = Type.meta, color = ink.faint)
            }
            Spacer(Modifier.height(Gap.xxl))
            return@ScaffoldPage
        }

        if (!open) {
            Spacer(Modifier.height(Gap.md))
            Sheet {
                Text(
                    text = if (before) "签到还没开始，到 ${d.startAt} 再来。" else if (after) "这一场已经结束。" else "这一场现在不能签到。",
                    fontSize = Type.body,
                    color = ink.muted,
                )
            }
            Spacer(Modifier.height(Gap.xxl))
            return@ScaffoldPage
        }

        if (d.needPhoto) {
            Spacer(Modifier.height(Gap.md))
            Sheet {
                Text("签到照片", fontSize = Type.title, fontWeight = FontWeight.Medium, color = ink.strong)
                Spacer(Modifier.height(Gap.sm))
                val bmp = photo?.preview
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "签到照片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ink.offFill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("还没有照片", fontSize = Type.meta, color = ink.faint)
                    }
                }
                Spacer(Modifier.height(Gap.md))
                if (shoot == null) {
                    Text(
                        "这一端没接相机，要照片的签到只能在系统相机那侧完成。",
                        fontSize = Type.meta,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    MiuixButton(
                        onClick = { shoot() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            if (bmp == null) "拍一张" else "重拍一张",
                            fontSize = Type.title,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(Gap.xs))
                    Text(
                        "按学校的签到要求拍：以宿舍为背景，至少和一名室友合拍。",
                        fontSize = Type.micro,
                        color = ink.faint,
                        lineHeight = Type.microLine,
                    )
                }
            }
        }

        Spacer(Modifier.height(Gap.md))
        Sheet {
            Text("当前位置", fontSize = Type.title, fontWeight = FontWeight.Medium, color = ink.strong)
            Spacer(Modifier.height(Gap.sm))
            val f = fix
            val bd = f?.let { Geo.wgsToBd(it.where.latitude, it.where.longitude) }
            if (f != null && bd != null) {
                Text(
                    text = "已取到位置" + if (locating) " · 正在等更准的一点…" else "",
                    fontSize = Type.body,
                    color = ink.body,
                )
                if (f.accuracyMeters > 0f) {
                    Text("精度 ±${f.accuracyMeters.roundToInt()} 米", fontSize = Type.micro, color = ink.faint)
                }
                Text(
                    text = latLng5(bd.longitude, bd.latitude),
                    fontSize = Type.micro,
                    color = ink.faint,
                )
            } else {
                Text(
                    text = if (locating) "正在定位…" else (locWhy ?: "还没取位置"),
                    fontSize = Type.body,
                    color = if (locReasonBad(locWhy, locating)) MaterialTheme.colorScheme.error else ink.muted,
                )
            }
            Spacer(Modifier.height(Gap.sm))
            Text(
                text = if (f == null) "取当前位置" else "重新定位",
                fontSize = Type.body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.pressClick(shrink = false) { askOrLocate() },
            )
        }

        Spacer(Modifier.height(Gap.lg))
        val canSubmit = !submitting && fix != null && (!d.needPhoto || photo != null)
        MiuixButton(
            onClick = { submit() },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(
                text = if (submitting) "正在提交…" else "提交签到",
                fontSize = Type.title,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(Gap.sm))
        Text(
            text = "只在你按下这颗按钮时发送。照片会先传到学校文件服务，再提交这一场签到。",
            fontSize = Type.micro,
            color = ink.faint,
            lineHeight = Type.microLine,
        )
        note?.let {
            Spacer(Modifier.height(Gap.sm))
            Text(
                text = it,
                fontSize = Type.meta,
                color = if (noteBad) MaterialTheme.colorScheme.error else good,
            )
        }
        Spacer(Modifier.height(Gap.xxl))
    }
}

/** 校方给的是"日期 + 时分"两段，拼成一条 ISO 交给共享层那个解析器（读不出来回 null）。 */
private fun parseAt(date: String, time: String): LocalDateTime? {
    if (date.isBlank() || time.isBlank()) return null
    return parsePatternTime("$date $time")
}

private fun withMeter(raw: String): String {
    val t = raw.trim()
    return if (t.isNotEmpty() && t.all { it.isDigit() || it == '.' }) "$t 米" else t
}

private fun locReasonBad(reason: String?, locating: Boolean): Boolean =
    !locating && !reason.isNullOrBlank() && (reason.contains("没给") || reason.contains("没拿到"))
