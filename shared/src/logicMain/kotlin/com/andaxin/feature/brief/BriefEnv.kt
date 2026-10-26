package com.andaxin.feature.brief

import com.andaxin.core.store.SessionStore

/**
 * 共享层这批列表/详情/表单屏要 `SessionStore`（判断"这句失败是不是因为没登录"、
 * 表单预填那一行宿舍），但把 session 一路穿到每一层签名上，改动面会铺到 RootShell 和
 * iOS 两套调用点。所以跟 [PhotoHost] 同一个办法：宿主建图时插一下。
 *
 * 安卓 = AppGraph 构造里插；iOS = IosAppGraph 构造里插。两端都必须插，
 * 所以用 lateinit：忘了插是当场炸在启动那一下，而不是某个按钮按下去没反应。
 */
object BriefEnv {
    lateinit var session: SessionStore

    /** 十几条数据源。安卓由 AppGraph 插上，iOS 由 IosAppGraph 插上 —— 两边都实现 [BriefGraph]。 */
    lateinit var graph: BriefGraph
}
