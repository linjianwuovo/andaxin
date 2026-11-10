package com.andaxin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.AndaXinTheme
import com.andaxin.core.settings.Appearance
import com.andaxin.core.settings.BarMaterial
import com.andaxin.shell.RootShell
import dagger.hilt.android.AndroidEntryPoint
import io.github.neoruaa.glasmio.glass.render.configureGlassDynamicRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /** 点通知要落到第几格（`Shelf` 的序号）。见 core/notify/MessageNotify。 */
        const val EXTRA_TAB = "andaxin.extra.TAB"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as AndaXinApp).graph
        askTab(graph, intent)
        /*
         * HDR 输出（ios27 那套液态玻璃的高光就靠它）。
         *
         * 窗口的色彩模式必须在这一层材质建立之前定好 —— 建完再改，那几条已经画出去的
         * 通道还是旧的，得整屏重建才有用。所以沿用上游的顺序：先读一次设置，
         * configureGlassDynamicRange 下发了再 setContent，而不是在组合里补一脚。
         *
         * 读设置这一步放协程里而不是 runBlocking：DataStore 是磁盘读，主线程上堵它是
         * 首帧卡顿的老来源。代价是多一帧空白，和上游 MeiloX 完全一样。
         */
        lifecycleScope.launch {
            // 读坏了也得有一份能画的：首选项读失败就退回出厂外观，绝不能让这一屏永远不 setContent
            val initial = runCatching { graph.appearance.state.first() }.getOrDefault(Appearance())
            applyGlassDynamicRange(initial)
            setContent {
                // 明暗在根上决定：底栏那层玻璃和整屏必须同一套色，否则暗色下栏体还是白的
                val appearance by graph.appearance.state
                    .collectAsStateWithLifecycle(initialValue = initial)
                // 这一档改了就得重建 Activity：色彩模式是窗口级的，改完不重建等于没改。
                // key 用推导出来的那个布尔而不是 hdr/material 两个原始值：只改实心档
                // 在 hdr 关着时不该白闪一次重建。
                LaunchedEffect(glassHdrOutput(appearance)) {
                    if (glassHdrOutput(appearance) != glassHdrOutput(initial)) recreate()
                }
                AndaXinTheme(dayNight = appearance.dayNight, accentHex = appearance.accent) {
                    CompositionLocalProvider(LocalAppGraph provides graph) {
                        RootShell()
                    }
                }
            }
        }
    }

    /**
     * 把"要不要 HDR 输出"落到窗口上。
     *
     * 实心那一档一层玻璃都不画（见 Appearance.spec()），请求 HDR 只是白占一条宽色域通道，
     * 所以这一档强制关掉 —— 和上游 `hdr && liquidGlass && !reduceTransparency` 同一条口径。
     */
    private fun applyGlassDynamicRange(appearance: Appearance) {
        configureGlassDynamicRange(window, hdrEnabled = glassHdrOutput(appearance))
    }

    private fun glassHdrOutput(appearance: Appearance): Boolean =
        appearance.hdr && appearance.material != BarMaterial.Solid

    /**
     * App 已经开着的时候点通知，系统走的是这一条（PendingIntent 带了 SINGLE_TOP），
     * 不会再走 onCreate —— 所以那句"落到哪一格"也得在这儿落一次，不然点通知只是把 App 捞回前台，
     * 还停在原来那一格上。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        askTab((application as AndaXinApp).graph, intent)
    }

    private fun askTab(graph: com.andaxin.app.AppGraph, i: Intent?) {
        val tab = i?.getIntExtra(EXTRA_TAB, -1) ?: -1
        if (tab >= 0) graph.tabJump.value = tab
    }
}
