package com.andaxin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.AndaXinTheme
import com.andaxin.core.settings.Appearance
import com.andaxin.shell.RootShell

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
        setContent {
            // 明暗在根上决定：底栏那层玻璃和整屏必须同一套色，否则暗色下栏体还是白的
            val appearance by graph.appearance.state
                .collectAsStateWithLifecycle(initialValue = Appearance())
            AndaXinTheme(dayNight = appearance.dayNight, accentHex = appearance.accent) {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    RootShell()
                }
            }
        }
    }

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
