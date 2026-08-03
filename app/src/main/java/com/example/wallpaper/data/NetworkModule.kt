package com.example.wallpaper.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 数据层（Data Layer）：网络客户端单例。
 *
 * 采用 OkHttp + 协程方案，避免在 UI 线程做网络请求导致 ANR。
 * 这里配置超时与连接池；重试逻辑在 Repository 中实现。
 */
object NetworkModule {

    /** 全局唯一 OkHttpClient，供下载复用连接，性能更优 */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)   // 图片流可能较大，读取超时放宽
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)      // 连接失败自动重试
            .followRedirects(true)               // 部分图床会 302 跳转，需跟随
            .followSslRedirects(true)
            .build()
    }
}