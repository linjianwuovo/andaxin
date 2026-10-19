package com.andaxin.core.poll

import com.andaxin.data.MessageFeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 应用内的消息轮询。
 *
 * 只做"开着的时候每几分钟拉一次"。真正的后台定时拉要 WorkManager，那一层要等消息接口
 * 接上、并且确认学校那边没有更长的推送通道之后再决定加不加 —— 现在加就是个空转的定时器。
 */
class Poller(private val messages: MessageFeed, private val scope: CoroutineScope) {

    private val _lastRunAt = MutableStateFlow(0L)
    val lastRunAt: StateFlow<Long> = _lastRunAt.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun start(intervalMillis: Long = 5 * 60 * 1000L) {
        scope.launch {
            while (true) {
                tick()
                delay(intervalMillis)
            }
        }
    }

    suspend fun tick() {
        _busy.value = true
        val r = try { messages.page(0) } catch (e: Exception) { Result.failure(e) }
        _unread.value = r.getOrDefault(emptyList()).count { it.unread }
        _lastRunAt.value = System.currentTimeMillis()
        _busy.value = false
    }
}
