package com.andaxin.feature.brief

import com.andaxin.data.brief.DetailSource
import com.andaxin.data.brief.FormSource
import com.andaxin.data.brief.ListSource

/**
 * 「简报」这一族屏（列表 / 详情 / 表单）向宿主唯一索取的东西：十几条数据源。
 *
 * 为什么是接口而不是直接拿 AppGraph：AppGraph 住在 app/，是安卓的建图，
 * iOS 那份 IosAppGraph 在共享层，两边都是手写的依赖图、谁也不是谁的儿子。
 * 屏只认这 19 个口，于是同一份 Modules.kt 两端都能跑，加一块屏也不用改建图。
 *
 * 一条口在某一端还没接上时，那端**也要给它一个值** —— 给 `NeedLoginList()` 那种
 * 会明确回一句话的占位，而不是留空：留空就是编译不过，而编译不过会诱使我去写
 * `?: null` 之类的兜底，那才是真把"没接上"这件事藏起来。
 */
interface BriefGraph {
    val examList: ListSource
    val examDetail: DetailSource
    val creditList: ListSource
    val creditDetail: DetailSource
    val laborList: ListSource
    val laborDetail: DetailSource
    val holidayList: ListSource
    val holidayDetail: DetailSource
    val repairList: ListSource
    val repairDetail: DetailSource
    val repairForm: FormSource
    val affairList: ListSource
    val toReadList: ListSource
    val myAffairList: ListSource
    val handledList: ListSource
    val affairDetail: DetailSource
    val affairForm: FormSource
    val dormList: ListSource
    val dormDetail: DetailSource

    /**
     * 下面这六条不是 `*Source`，是 capture 类本身 —— 因为用它们的那三块屏（查寝看板、校园卡、
     * AI 课堂）不走 BriefKit 那个"列表/详情/表单"形状：查寝看板要的是月概览 + 月历 + 两节任务
     * 一份整体状态，校园卡要的是余额 + 流水 + 那把第三方 token，AI 课堂要的是 FIF 那套独立鉴权。
     * 硬塞进 `ListSource` 会把它们各自的形状抹平。
     */
    val dormBoard: com.andaxin.data.capture.CaptureDormBoard

    /**
     * 列表那一发带来的「原文件直链 + 校方那个 isDownload 标记」，从列表点进课件预览时递一手。
     * 直链是 `resourceUrl`（minio 预签名，实测 401 字符），不进路由串 —— 太长且带签名。
     */
    val coursewareFile: kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>
    val card: com.andaxin.data.capture.CampusCardClient
    val fif: com.andaxin.data.capture.FifSession
    val fifApi: com.andaxin.data.capture.FifApi
    val notes: com.andaxin.data.capture.NoteApi
    val aiCourses: com.andaxin.data.capture.AiCourseFeed
    /** 「班级群」那一屏要的班内名单口（CaptureIm，两端同一个类）。 */
    val im: com.andaxin.data.capture.CaptureIm
    /**
     * 下面四条不是简报那一族的，是「主题」「通知设置」「关于」这三块屏要的：
     * 外观存储、那份首选项、课程表口、以及那一档调试插座（正式版给 null）。
     */
    val appearance: com.andaxin.core.settings.AppearanceStore
    val store: com.andaxin.core.store.Store
    val timetable: com.andaxin.data.TimetableFeed
    val debug: com.andaxin.core.debug.DebugPanel?

    /** 运动那一屏的三条口：定位源、跑步引擎、校方那台体育数据口。 */
    val gps: com.andaxin.core.location.GpsSource
    val engine: com.andaxin.core.run.RunEngine
    val sports: com.andaxin.data.SportsFeed
}
