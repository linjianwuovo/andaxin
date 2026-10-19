package com.andaxin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.andaxin.app.LocalAppGraph
import com.andaxin.core.design.AndaXinTheme
import com.andaxin.shell.RootShell

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as AndaXinApp).graph
        setContent {
            AndaXinTheme {
                CompositionLocalProvider(LocalAppGraph provides graph) {
                    RootShell()
                }
            }
        }
    }
}
