package com.andaxin.core.poll

import android.content.Context
import com.andaxin.core.notify.MessageNotify
import com.andaxin.core.store.Store
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
 *
 * 拉到之后顺手做两件事（10-25 他一句"消息tab里的通知也要通知"）：
 * ① 底栏那颗角标跟着刷新（`Unread.messages`，和消息屏写的是同一份）；
 * ② 有没见过的新消息就报一条通知，读光了就把那条撤掉 —— 判定和撤的规矩写在
 *   `core/notify/MessageNotify.kt` 顶上，尤其是"第一趟只记名单不报历史"那一条。
 */
class Poller(
    private val messages: MessageFeed,
    private val context: Context,
    private val store: Store,
    private val scope: CoroutineScope,
) {

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
        _lastRunAt.value = System.currentTimeMillis()
        _busy.value = false
        val items = r.getOrNull()
        // 拉失败什么都不改：不把"没连上"报成"没新消息"，也不撤他栏子里那条真的
        if (items == null) return
        val n = items.count { it.unread }
        _unread.value = n
        com.andaxin.core.store.Unread.messages.value = n
        val p = store.snapshot()
        val raw = store.peekString(p, MessageNotify.KEY_SEEN)
        val seen = raw?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
        val plan = MessageNotify.plan(items, seen, firstRun = raw == null)
        val fresh = plan.fresh
        when {
            fresh != null -> MessageNotify.post(context, fresh)
            // 未读归零 = 他读完了，这条通知就该自己消失
            n == 0 -> MessageNotify.clear(context)
        }
        store.setMany(MessageNotify.KEY_SEEN to plan.seen.joinToString(","))
    }
}
