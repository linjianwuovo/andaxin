package com.andaxin.shared

import com.andaxin.core.schedule.CalendarEvent
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKSpan
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/*
 * iOS 这一端「把这一周的课写进系统日历」：EventKit 直接往用户自己那份日历里写。
 *
 * 为什么不走"导出 .ics 再让他挑日历"：那要存文件、再在分享面板里挑一次"日历"，
 * 两步都只是为了绕开权限。EventKit 只要一次授权，之后点一下就进去了。
 *
 * 绑定形状是从 `eventkit_dump.txt` 里查的，不是猜的：
 * - `EKEventStore.requestFullAccessToEventsWithCompletion((Boolean, NSError?) -> Unit)`
 *   （iOS 17 起的那条；老的 `requestAccessToEntityType` 也在，但新系统上不弹了）
 * - `EKEvent.eventWithEventStore(store)` 回**非空** EKEvent
 * - `saveEvent(event, span, commit, error)` 回 Boolean，逐个 commit=true 就行（一批十来条）
 * - `defaultCalendarForNewEvents` 是 val、可空 —— 没有默认日历就明说，不静默丢
 *
 * 权限被拒时那句人话要能指路（设置 → 隐私 → 日历），不是一句"失败了"。
 */
@OptIn(ExperimentalForeignApi::class)
internal fun addEventsToCalendar(events: List<CalendarEvent>, onResult: (Boolean, String) -> Unit) {
    val store = EKEventStore()
    fun report(ok: Boolean, msg: String) {
        dispatch_async(dispatch_get_main_queue()) { onResult(ok, msg) }
    }
    store.requestFullAccessToEventsWithCompletion { granted: Boolean, _: NSError? ->
        if (!granted) {
            report(false, "没拿到日历权限：设置 → 隐私与安全性 → 日历 里给「安大信」打开，再点一次")
            return@requestFullAccessToEventsWithCompletion
        }
        val cal = store.defaultCalendarForNewEvents
        if (cal == null) {
            report(false, "这台机没有可写的默认日历（系统「日历」里先建一个，或者把 iCloud 日历打开）")
            return@requestFullAccessToEventsWithCompletion
        }
        var added = 0
        var lastError = ""
        events.forEach { e ->
            val ev = EKEvent.eventWithEventStore(store)
            ev.title = e.title
            ev.startDate = NSDate.dateWithTimeIntervalSince1970(e.startMillis / 1000.0)
            ev.endDate = NSDate.dateWithTimeIntervalSince1970(e.endMillis / 1000.0)
            if (e.location.isNotBlank()) ev.location = e.location
            ev.notes = e.notes
            ev.calendar = cal
            val ok = memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                val saved = store.saveEvent(ev, EKSpan.EKSpanThisEvent, true, err.ptr)
                if (!saved) lastError = err.value?.localizedDescription.orEmpty()
                saved
            }
            if (ok) added++
        }
        report(
            added > 0,
            if (added == events.size) {
                "已写进日历：$added 条（在系统「日历」里能改能删，重复点会重复加）"
            } else if (added == 0) {
                "一条都没写进去${if (lastError.isBlank()) "" else "：$lastError"}"
            } else {
                "写进去 $added 条，另外 ${events.size - added} 条没成${if (lastError.isBlank()) "" else "：$lastError"}"
            },
        )
    }
}
