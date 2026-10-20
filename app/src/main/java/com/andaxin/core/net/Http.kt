package com.andaxin.core.net

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * 一个连接池，全应用共用。
 *
 * 校方那几个端点（in.aiit / fdygl / ywlz / repair）在校园里握手普遍 300ms 以上，
 * 超时给太短会直接表现为"这一屏刷不出来"，所以读超时放宽到 20s，但连接池留小，
 * 免得十几个端点各占一条长连接。
 */
object Http {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
            // 上面**不要**再写 connectionSpecs(...)：
            // 10-20 中午这里写的是 listOf(COMPATIBLE_TLS, MODERN_TLS)，
            // 把默认列表里那条 `ConnectionSpec.CLEARTEXT` 挤掉了，于是 OkHttp 自己就把明文请求拒了，
            // 报的还是那句 "CLEARTEXT communication to … not permitted by network security policy" ——
            // 看着像 Android 的策略在拦，其实是我们把能力关掉了，
            // 害我照着 manifest 的 networkSecurityConfig / usesCleartextTraffic 猜错两轮。
            // 分层应该是这样：**OkHttp 具备明文能力，允不允许由平台那份 NSC 决定**
            // （res/xml/network_security_config.xml 里只放行 sports.aiit.edu.cn 一个域名）。
    }
}
