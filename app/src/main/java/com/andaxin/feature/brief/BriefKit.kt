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
import androidx.compose.material3.TextField
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
import com.andaxin.core.design.Gap
import com.andaxin.core.design.LocalInk
import com.andaxin.core.design.Palette
import com.andaxin.core.design.Radius
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
import com.andaxin.shell.PlaceholderNote
import com.andaxin.shell.Sheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/* ───────────────────────── 状态 ───────────────────────── */

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
internal data class DetailUi(val data: BriefDetail? = null, val error: String? = null)

internal class DetailModel(private val source: DetailSource, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(DetailUi())
    val state: StateFlow<DetailUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val r = try { source.detail(id) } catch (e: Exception) { Result.failure(e) }
            _state.value = DetailUi(data = r.getOrNull(), error = r.exceptionOrNull()?.message)
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
    note: String = "这一屏的数据是占位，还没接真实报文。",
    empty: String = "暂时没有条目。",
) {
    val model = adxViewModel { ListModel(source) }
    val state by model.state.collectAsStateWithLifecycle()
    val ink = LocalInk.current

    ScaffoldPage(title = title, onBack = onBack) {
        if (state.rows.isEmpty() && !state.loading) {
            Text(empty, fontSize = 14.sp, color = ink.faint)
        }
        state.rows.forEach { row ->
            Sheet(modifier = Modifier.clickable { onRow(row.id) }) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(row.title, fontSize = 15.sp, color = ink.strong)
                        if (row.sub.isNotBlank()) {
                            Spacer(Modifier.height(Gap.xs))
                            Text(row.sub, fontSize = 12.sp, color = ink.muted)
                        }
                    }
                    if (row.right.isNotBlank()) {
                        Text(
                            text = row.right,
                            fontSize = 12.sp,
                            color = when (row.flag) {
                                Flag.Ok -> Palette.Teal
                                Flag.Bad -> MaterialTheme.colorScheme.error
                                Flag.Warn -> Palette.Amber
                                Flag.Unread -> MaterialTheme.colorScheme.primary
                                Flag.None -> ink.faint
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Gap.sm))
        }
        state.error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
        PlaceholderNote(note)
    }
}

@Composable
fun BriefDetailPage(
    title: String,
    source: DetailSource,
    id: String,
    onBack: () -> Unit,
    note: String = "字段还没接真实报文，正文里不放编出来的话。",
) {
    val model = adxViewModel { DetailModel(source, id) }
    val state by model.state.collectAsStateWithLifecycle()
    val ink = LocalInk.current

    ScaffoldPage(title = state.data?.title ?: title, onBack = onBack) {
        val d = state.data
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
        }
        state.error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(Gap.md))
        PlaceholderNote(note)
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

    ScaffoldPage(title = state.spec?.title ?: title, onBack = onBack) {
        state.spec?.fields?.forEach { field ->
            FieldRow(field, get = { values[it].orEmpty() }, put = { k, v -> values[k] = v })
            Spacer(Modifier.height(Gap.md))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(MaterialTheme.colorScheme.primary, Radius.card)
                .clickable { model.submit(values.toMap()) },
            contentAlignment = Alignment.Center,
        ) {
            Text("提交", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimary)
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
        PlaceholderNote("提交这一侧还没接真实报文，按下去只会告诉你没接。")
    }
}

@Composable
private fun FieldRow(field: Field, get: (String) -> String, put: (String, String) -> Unit) {
    val ink = LocalInk.current
    when (field) {
        is Field.Text -> Column {
            Text(field.label, fontSize = 12.sp, color = ink.muted)
            Spacer(Modifier.height(Gap.xs))
            TextField(
                value = get(field.key),
                onValueChange = { put(field.key, it) },
                placeholder = { Text(field.hint, fontSize = 12.sp, color = ink.ghost) },
                singleLine = true,
            )
        }
        is Field.Person -> Labeled(label = field.label, value = "3 号楼 412（取本机设置，批次三接）")
        is Field.Number -> Labeled(label = field.label, value = "未填 · ${field.unit}")
        is Field.Date -> Labeled(label = field.label, value = "点选日期（日期控件批次三补）")
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

@Composable
fun ScaffoldPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Gap.screen),
    ) {
        Spacer(Modifier.height(Gap.xl))
        Text(
            "‹ 返回",
            fontSize = 14.sp,
            color = LocalInk.current.muted,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = Gap.sm),
        )
        Spacer(Modifier.height(Gap.sm))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = LocalInk.current.strong)
        Spacer(Modifier.height(Gap.lg))
        content()
        Spacer(Modifier.height(Gap.xxl))
    }
}
