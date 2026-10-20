package com.andaxin.feature.brief

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
import com.andaxin.core.design.Type
import com.andaxin.data.brief.BriefDetail
import com.andaxin.data.brief.BriefRow
import com.andaxin.data.brief.DetailSource
import com.andaxin.data.brief.Field
import com.andaxin.data.brief.Flag
import com.andaxin.data.brief.FormSpec
import com.andaxin.data.brief.FormSource
import com.andaxin.data.brief.ListSource
import com.andaxin.shell.Hairline
import com.andaxin.shell.adxViewModel
import com.andaxin.shell.Labeled
import com.andaxin.shell.RowLine
import com.andaxin.shell.Sheet
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* ───────────────────────── 状态 ───────────────────────── */

/** 按下去就是真发的那一档：底色给错误色，字给白。 */
@Composable
private fun dangerColors() = ButtonDefaults.buttonColors(
    color = MaterialTheme.colorScheme.error,
    disabledColor = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
    contentColor = androidx.compose.ui.graphics.Color.White,
    disabledContentColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
)

@Immutable
internal data class ListUi(val rows: List<BriefRow> = emptyList(), val error: String? = null, val loading: Boolean = true)

internal class ListModel(private val source: ListSource) : ViewModel() {
    private val _state = MutableStateFlow(ListUi())
    val state: StateFlow<ListUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val r = try { source.rows() } catch (e: Exception) { Result.failure(e) }
            _state.value = ListUi(rows = r.getOrDefault(emptyList()), error = r.exceptionOrNull()?.message, loading = false)
        }
    }
}

@Immutable
internal data class DetailUi(
    val data: BriefDetail? = null,
    val error: String? = null,
    val done: String? = null,
    val acting: Boolean = false,
    /**
     * 详情那一屏以前没有这一档：数据没回来、错误也没有的时候，页面就是一片空白加底下那句说明，
     * 看着像"这单什么都没有了"。10-20 读待办详情时实测过：审批链加表单要七八秒，
     * 那七八秒里屏幕上就是空的。
     */
    val loading: Boolean = true,
)

internal class DetailModel(private val source: DetailSource, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(DetailUi())
    val state: StateFlow<DetailUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val r = try { source.detail(id) } catch (e: Exception) { Result.failure(e) }
            _state.value = DetailUi(
                data = r.getOrNull(),
                error = r.exceptionOrNull()?.message,
                loading = false,
            )
        }
    }

    /** 详情里那颗写操作键。界面已经连按两次确认过才会走到这儿。 */
    fun act() {
        if (_state.value.acting) return
        _state.value = _state.value.copy(acting = true, error = null)
        viewModelScope.launch {
            val r = try { source.act(id) } catch (e: Exception) { Result.failure(e) }
            _state.value = _state.value.copy(
                acting = false,
                done = r.getOrNull(),
                error = r.exceptionOrNull()?.message,
            )
        }
    }
}

@Immutable
internal data class FormUi(val spec: FormSpec? = null, val error: String? = null, val done: String? = null)

internal class FormModel(private val source: FormSource) : ViewModel() {
    private val _state = MutableStateFlow(FormUi())
    val state: StateFlow<FormUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val r = try { source.spec() } catch (e: Exception) { Result.failure(e) }
            _state.value = FormUi(spec = r.getOrNull(), error = r.exceptionOrNull()?.message)
        }
    }

    fun submit(values: Map<String, String>) {
        viewModelScope.launch {
            val r = try { source.submit(values) } catch (e: Exception) { Result.failure(e) }
            _state.value = _state.value.copy(done = r.getOrNull(), error = r.exceptionOrNull()?.message)
        }
    }
}

/* ───────────────────────── 三种页 ───────────────────────── */

@Composable
fun BriefListPage(
    title: String,
    source: ListSource,
    onRow: (String) -> Unit,
    onBack: () -> Unit,
    empty: String = "暂时没有条目。",
    newLabel: String? = null,
    onNew: (() -> Unit)? = null,
) {
    val model = adxViewModel { ListModel(source) }
    val state by model.state.collectAsStateWithLifecycle()
    val ink = LocalInk.current

    ScaffoldPage(title = title, onBack = onBack) {
        if (state.loading && state.rows.isEmpty()) {
            Text("正在拉…", fontSize = Type.meta, color = ink.faint)
        } else if (state.rows.isEmpty() && !state.loading) {
            Text(empty, fontSize = Type.body, color = ink.faint)
        }
        // 「新建」那一行放在**列表上面**。它原来画在所有行的下面 —— 待办那一屏有 21 行，
        // 于是「＋ 发起请假」被推到屏幕外，装机时读屏根本找不到它（10-20 读屏验出来）。
        // 有 onNew 才画这一行，没有就一行都不多。
        if (newLabel != null && onNew != null) {
            Sheet(modifier = Modifier.clickable(onClick = onNew)) {
                Text(newLabel, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(Gap.sm))
        }
        // 一张卡里一行一条、中间分隔线。以前是一条一张卡：二十条待办就是二十张卡，
        // 卡之间的缝比内容还显眼，滚两屏才看完 —— 这就是"肿"。
        if (state.rows.isNotEmpty()) {
            Sheet {
                state.rows.forEachIndexed { i, row ->
                    RowLine(
                        title = row.title,
                        sub = row.sub,
                        right = row.right,
                        flag = row.flag,
                        onClick = { onRow(row.id) },
                    )
                    if (i != state.rows.lastIndex) {
                        Spacer(Modifier.height(Gap.xs))
                        Hairline()
                        Spacer(Modifier.height(Gap.xs))
                    }
                }
            }
        }
        state.error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Gap.sm))
    }
}

@Composable
fun BriefDetailPage(
    title: String,
    source: DetailSource,
    id: String,
    onBack: () -> Unit,
) {
    val model = adxViewModel { DetailModel(source, id) }
    val state by model.state.collectAsStateWithLifecycle()
    val ink = LocalInk.current
    // 写操作要连按两次：第一次只是把键变成"再按一次真的销假"，第二次才发报文
    var armed by remember { mutableStateOf(false) }

    ScaffoldPage(title = state.data?.title ?: title, onBack = onBack) {
        val d = state.data
        if (d == null && state.loading && state.error == null) {
            Text("正在拉这一条…", fontSize = 13.sp, color = ink.faint)
        }
        if (d != null) {
            Sheet {
                d.fields.forEachIndexed { i, (label, value) ->
                    Labeled(label = label, value = value)
                    if (i != d.fields.lastIndex) {
                        Spacer(Modifier.height(Gap.sm)); Hairline(); Spacer(Modifier.height(Gap.sm))
                    }
                }
            }
            d.paragraphs.forEach {
                Spacer(Modifier.height(Gap.md))
                Text(it, fontSize = 14.sp, color = ink.body, lineHeight = 21.sp)
            }
            if (d.writable) {
                Spacer(Modifier.height(Gap.lg))
                    // 写操作那颗键走 Miuix 的 Button；armed 那档换成错误色，
                // 让"这一下是真发"在颜色上就看得出来，不靠多写一行字
                MiuixButton(
                    onClick = {
                        if (armed) {
                            armed = false
                            model.act()
                        } else {
                            armed = true
                        }
                    },
                    enabled = !state.acting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (armed) dangerColors() else ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = when {
                            state.acting -> "正在发…"
                            armed -> "再按一次，真的${d.writeLabel.ifBlank { "提交" }}"
                            else -> d.writeLabel.ifBlank { "提交" }
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        state.done?.let {
            Spacer(Modifier.height(Gap.md))
            Text(it, fontSize = 13.sp, color = Palette.Teal, lineHeight = 19.sp)
        }
        state.error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Gap.md))
    }
}

@Composable
fun BriefFormPage(
    title: String,
    source: FormSource,
    onBack: () -> Unit,
) {
    val model = adxViewModel { FormModel(source) }
    val state by model.state.collectAsStateWithLifecycle()
    val values = remember { mutableStateMapOf<String, String>() }
    val ink = LocalInk.current
    val dorm = LocalAppGraph.current.let { g ->
        val s by g.session.state.collectAsStateWithLifecycle(com.andaxin.core.store.Session())
        s.dorm
    }

    ScaffoldPage(title = state.spec?.title ?: title, onBack = onBack) {
        state.spec?.fields?.forEach { field ->
            FieldRow(
                field,
                get = { values[it].orEmpty() },
                put = { k, v -> values[k] = v },
                dorm = dorm,
            )
            Spacer(Modifier.height(Gap.md))
        }
        MiuixButton(
            onClick = { model.submit(values.toMap()) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text("提交", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        state.done?.let {
            Spacer(Modifier.height(Gap.md))
            Text("已提交：$it", fontSize = 13.sp, color = Palette.Teal)
        }
        state.error?.let {
            Spacer(Modifier.height(Gap.md))
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Gap.md))
    }
}

@Composable
private fun FieldRow(
    field: Field,
    get: (String) -> String,
    put: (String, String) -> Unit,
    dorm: String,
) {
    val ink = LocalInk.current
    var picking by remember { mutableStateOf(false) }
    when (field) {
        is Field.Text -> Column {
            Text(field.label, fontSize = 12.sp, color = ink.muted)
            Spacer(Modifier.height(Gap.xs))
            MiuixTextField(
                value = get(field.key),
                onValueChange = { put(field.key, it) },
                // Miuix 这个输入框没有 placeholder 槽，提示语走 `label`
                label = field.hint,
                singleLine = true,
            )
        }
        // 宿舍这种"本机设置里填的东西"：填了什么就显示什么，没填就明说去哪儿填，
        // 不拿一个假房间号顶着 —— 那会让报修单看着像已经填好了
        is Field.Person -> Labeled(
            label = field.label,
            value = dorm.ifBlank { "没填 · 去「我的 · 宿舍」填一下" },
        )
        is Field.Number -> Labeled(label = field.label, value = "未填 · ${field.unit}")
        // 日期终于能点了。以前这一行只写着"（日期控件批次三补）"，
        // 而 DatePicker 的 import 一直挂在这个文件里没人用 —— 表单里所有日期字段都填不上。
        is Field.Date -> {
            if (picking) {
                val ps = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { picking = false },
                    confirmButton = {
                        Text(
                            text = "选定",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    ps.selectedDateMillis?.let(::formatUtcDay)?.let { put(field.key, it) }
                                    picking = false
                                }
                                .padding(horizontal = Gap.md, vertical = Gap.sm),
                        )
                    },
                    dismissButton = {
                        Text(
                            text = "取消",
                            fontSize = 14.sp,
                            color = ink.muted,
                            modifier = Modifier
                                .clickable { picking = false }
                                .padding(horizontal = Gap.md, vertical = Gap.sm),
                        )
                    },
                ) {
                    DatePicker(state = ps, showModeToggle = false)
                }
            }
            val chosen = get(field.key)
            Column(Modifier.clickable { picking = true }) {
                Text(field.label, fontSize = 12.sp, color = ink.muted)
                Spacer(Modifier.height(Gap.xs))
                Text(
                    text = chosen.ifBlank { "点选日期" },
                    fontSize = 15.sp,
                    color = if (chosen.isBlank()) ink.ghost else ink.strong,
                )
            }
        }
        is Field.Choice -> Column {
            Text(field.label, fontSize = 12.sp, color = ink.muted)
            Spacer(Modifier.height(Gap.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.sm)) {
                field.options.forEach { opt ->
                    Text(
                        text = opt,
                        fontSize = 13.sp,
                        color = ink.body,
                        modifier = Modifier
                            .background(ink.hairline, Radius.pill)
                            .clickable { put(field.key, opt) }
                            .padding(horizontal = Gap.md, vertical = Gap.sm),
                    )
                }
            }
        }
    }
}

/**
 * M3 的 DatePicker 给的是 **UTC 零点**的毫秒数，所以这里也按 UTC 格式化。
 * 换成机器时区的话，在东八区没事、在西五区就会把用户点的日期整整挪后一天。
 */
private fun formatUtcDay(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneOffset.UTC)
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

/**
 * 三种页共用的那一层壳。
 *
 * 换成 Miuix 的 `Scaffold` + `SmallTopAppBar`：HyperOS 那一档标题栏自带状态栏与
 * 刘海的留白，以前是我们手搓一个 `Spacer(Gap.xl)` 顶出来的，换个机型就得重调一次。
 * 返回那颗键仍然写"‹ 返回"三个字 —— 这仓的返回入口一直是这个说法，不换图标。
 */
@Composable
fun ScaffoldPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                navigationIcon = {
                    Text(
                        "‹ 返回",
                        fontSize = 14.sp,
                        color = LocalInk.current.muted,
                        modifier = Modifier.clickable(onClick = onBack).padding(horizontal = Gap.md, vertical = Gap.sm),
                    )
                },
            )
        },
        containerColor = topBarBackdrop(),
    ) { bars ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(bars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gap.screen),
        ) {
            Spacer(Modifier.height(Gap.md))
            content()
            Spacer(Modifier.height(Gap.xxl))
        }
    }
}

@Composable
private fun topBarBackdrop() = MiuixTheme.colorScheme.background
