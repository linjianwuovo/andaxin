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
            .connectionSpecs(
                listOf(
                    okhttp3.ConnectionSpec.COMPATIBLE_TLS,
                    okhttp3.ConnectionSpec.MODERN_TLS,
                )
            )
            .build()
    }
}
