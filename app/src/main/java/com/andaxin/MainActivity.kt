package com.andaxin

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as AndaXinApp).graph
        setContent {
            // 明暗在根上决定：底栏那层玻璃和整屏必须同一套色，否则暗色下栏体还是白的
            val appearance by graph.appearance.state
                .collectAsStateWithLifecycle(initialValue = Appearance())
            AndaXinTheme(dayNight = appearance.dayNight) {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    RootShell()
                }
            }
        }
    }
}
