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
}
