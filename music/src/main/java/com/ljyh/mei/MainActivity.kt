package com.ljyh.mei

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.media3.common.util.UnstableApi
import com.ljyh.mei.utils.log.CrashHandler
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import com.ljyh.mei.utils.log.FileLoggingTree

/**
 * MeiloX 音乐屏的独立入口，只剩一层壳。
 *
 * 音乐屏本体已经搬进 [MusicScreen]，宿主（com.andaxin）把同一份内容当自己的一页渲染。
 * 这个 Activity 留着，是为了还能单独把音乐 App 装起来跑（AndroidManifest 里它仍是
 * exported=false 的组件，只有宿主用 Intent 拉起时才会出现）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @androidx.annotation.OptIn(UnstableApi::class)
    @RequiresApi(Build.VERSION_CODES.S)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CrashHandler.init(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(FileLoggingTree(this))
        } else {
            Timber.plant(FileLoggingTree(this))
        }
        setContent {
            MusicScreen()
        }
    }
}
