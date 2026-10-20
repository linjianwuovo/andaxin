package com.andaxin.state

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andaxin.core.schedule.Periods
import com.andaxin.data.Dashboard
import com.andaxin.data.DashboardFeed
import com.andaxin.data.MessageFeed
import com.andaxin.data.MessageItem
import com.andaxin.data.NoticeBody
import com.andaxin.data.NoticeFeed
import com.andaxin.data.NoticeItem
import com.andaxin.data.TimetableFeed
import com.andaxin.data.TimetableSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * UiState 一律 @Immutable：Compose 靠这个决定要不要重绘，标错了就是白屏或掉帧。
 * 一次出：加载态只在"第一次什么都没有"时占位，刷新期间保留旧数据，别整屏闪一下骨架。
 */

@Immutable
data class HomeUiState(
    val firstLoad: Boolean = true,
    val data: Dashboard? = null,
    val today: List<TimetableSlot> = emptyList(),
    val error: String? = null,
)

/**
 * 首页。
 *
 * 一次出：仪表数据和今天的课两路并发取，都回来了才落一次 state ——
 * 不许一路加载一路蹦（他验收过的手感）。
 */
class HomeModel(
    private val feed: DashboardFeed,
    private val timetable: TimetableFeed,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { refresh() }
    }

    /**
     * 挂起版：下拉刷新要等这一趟真回来才收指示器，
     * 所以给一个能等的（[reload] 是后台起协程，不等）。
     */
    suspend fun refresh() {
        val dash = try { feed.snapshot() } catch (e: Exception) { Result.failure(e) }
        val slots = try { timetable.week(0) } catch (e: Exception) { Result.failure(e) }
        val list = slots.getOrDefault(emptyList())
        _state.value = HomeUiState(
            firstLoad = false,
            data = dash.getOrNull() ?: _state.value.data,
            // 今天星期几由 Periods 说，跟周网格、首页"几节课"同一张表 —— 这儿原来写死 ==1，
            // 周一看着对，周二起就把周一的课当今天显示（10-20 上午装机读屏撞中的就是这个）。
            today = list.filter { it.day == Periods.today() },
            error = (dash.exceptionOrNull() ?: slots.exceptionOrNull())?.message,
        )
    }
}

@Immutable
data class TimetableUiState(
    val firstLoad: Boolean = true,
    val weekOffset: Int = 0,
    val slots: List<TimetableSlot> = emptyList(),
    val error: String? = null,
)

class TimetableModel(private val feed: TimetableFeed) : ViewModel() {
    private val _state = MutableStateFlow(TimetableUiState())
    val state: StateFlow<TimetableUiState> = _state.asStateFlow()

    init {
        load(0)
    }

    fun load(offset: Int) {
        viewModelScope.launch { refresh(offset) }
    }

    /** 挂起版给下拉刷新用：等这一发回来才收指示器。 */
    suspend fun refresh(offset: Int = _state.value.weekOffset) {
        val r = try { feed.week(offset) } catch (e: Exception) { Result.failure(e) }
        _state.value = TimetableUiState(
            firstLoad = false,
            weekOffset = offset,
            slots = r.getOrDefault(_state.value.slots),
            error = r.exceptionOrNull()?.message,
        )
    }
}

@Immutable
data class MessageUiState(
    val firstLoad: Boolean = true,
    val items: List<MessageItem> = emptyList(),
    val error: String? = null,
    /** 标已读失败的原因。跟列表失败分开：列表好好的、只有这一条没标上，是两种事。 */
    val readError: String? = null,
)

class MessageModel(private val feed: MessageFeed) : ViewModel() {
    private val _state = MutableStateFlow(MessageUiState())
    val state: StateFlow<MessageUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { refresh() }
    }

    /** 挂起版给下拉刷新用：等这一发回来才收指示器。 */
    suspend fun refresh() {
        val r = try { feed.page(0) } catch (e: Exception) { Result.failure(e) }
        _state.value = MessageUiState(
            firstLoad = false,
            items = r.getOrDefault(_state.value.items),
            error = r.exceptionOrNull()?.message,
        )
    }

    /**
     * 标已读：**服务端说成了才把红点去掉**。
     *
     * 这不是洁癖 —— 官方前端就是这个顺序（`readPushMessage.do` 的 `.then` 里才改 `readFlag`）。
     * 本地先划掉的话，下一次刷新红点又回来，看着像 App 在乱记。
     * 失败的原因留在 [readError] 上，消息屏那一行下面会显示出来。
     */
    fun markRead(id: String) {
        viewModelScope.launch {
            val r = try { feed.markRead(id) } catch (e: Exception) { Result.failure(e) }
            if (r.isSuccess) {
                _state.value = _state.value.copy(
                    readError = null,
                    items = _state.value.items.map { if (it.id == id) it.copy(unread = false) else it },
                )
            } else {
                _state.value = _state.value.copy(readError = r.exceptionOrNull()?.message ?: "服务端没认这条")
            }
        }
    }
}

@Immutable
data class NoticeUiState(
    val firstLoad: Boolean = true,
    val items: List<NoticeItem> = emptyList(),
    val open: NoticeBody? = null,
    val error: String? = null,
)

class NoticeModel(private val feed: NoticeFeed) : ViewModel() {
    private val _state = MutableStateFlow(NoticeUiState())
    val state: StateFlow<NoticeUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { refresh() }
    }

    /** 挂起版给下拉刷新用：等这一发回来才收指示器。 */
    suspend fun refresh() {
        val r = try { feed.list() } catch (e: Exception) { Result.failure(e) }
        _state.value = _state.value.copy(
            firstLoad = false,
            items = r.getOrDefault(_state.value.items),
            error = r.exceptionOrNull()?.message,
        )
    }

    fun open(id: String) {
        viewModelScope.launch {
            val r = try { feed.detail(id) } catch (e: Exception) { Result.failure(e) }
            _state.value = _state.value.copy(open = r.getOrNull())
        }
    }

    fun close() {
        _state.value = _state.value.copy(open = null)
    }
}
