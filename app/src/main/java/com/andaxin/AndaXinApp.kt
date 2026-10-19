package com.andaxin

import android.app.Application
import com.andaxin.app.AppGraph

class AndaXinApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // 只建壳，feed 全是 lazy：启动期不做任何磁盘或网络 IO
        graph = AppGraph(this)
    }
}
