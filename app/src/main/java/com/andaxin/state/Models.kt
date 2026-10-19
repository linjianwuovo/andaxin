package com.andaxin.state

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val error: String? = null,
)

class HomeModel(private val feed: DashboardFeed) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val r = try { feed.snapshot() } catch (e: Exception) { Result.failure(e) }
            _state.value = HomeUiState(
                firstLoad = false,
                data = r.getOrNull() ?: _state.value.data,
                error = r.exceptionOrNull()?.message,
            )
        }
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
        viewModelScope.launch {
            val r = try { feed.week(offset) } catch (e: Exception) { Result.failure(e) }
            _state.value = TimetableUiState(
                firstLoad = false,
                weekOffset = offset,
                slots = r.getOrDefault(_state.value.slots),
                error = r.exceptionOrNull()?.message,
            )
        }
    }
}

@Immutable
data class MessageUiState(
    val firstLoad: Boolean = true,
    val items: List<MessageItem> = emptyList(),
    val error: String? = null,
)

class MessageModel(private val feed: MessageFeed) : ViewModel() {
    private val _state = MutableStateFlow(MessageUiState())
    val state: StateFlow<MessageUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val r = try { feed.page(0) } catch (e: Exception) { Result.failure(e) }
            _state.value = MessageUiState(
                firstLoad = false,
                items = r.getOrDefault(_state.value.items),
                error = r.exceptionOrNull()?.message,
            )
        }
    }

    /** 已读只在本地先落，服务端那一条要等他抓包补 markRead 的真实报文。 */
    fun markRead(id: String) {
        viewModelScope.launch {
            try { feed.markRead(id) } catch (_: Exception) { }
            _state.value = _state.value.copy(items = _state.value.items.map { if (it.id == id) it.copy(unread = false) else it })
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
        viewModelScope.launch {
            val r = try { feed.list() } catch (e: Exception) { Result.failure(e) }
            _state.value = _state.value.copy(firstLoad = false, items = r.getOrDefault(_state.value.items), error = r.exceptionOrNull()?.message)
        }
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
