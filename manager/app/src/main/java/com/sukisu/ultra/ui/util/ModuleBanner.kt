package com.sukisu.ultra.ui.util

import android.content.Context
import android.util.Log
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.theme.BackgroundConfig
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * 解析一个模块该显示哪张横幅，移植自 FolkPatch `APMModuleItem` 的 `bannerInfo` 计算。
 *
 * 优先级：**API 模式 > 用户自己指定的横幅 > 模块自带的 `banner` 属性/文件**。
 *
 * 进程内缓存，避免每次重组都过一遍 root shell；`reload` 用于用户改了横幅或切了 API 源之后
 * 强制重取。"取不到"也要缓存下来，否则一个没有横幅的模块会在每次重组时都去读一次 root。
 */
object ModuleBanner {
    private const val TAG = "ModuleBanner"
    private const val MODULES_ROOT = "/data/adb/modules"

    /**
     * 内存里最多留几张横幅。
     *
     * 横幅是整张图片的字节，一个模块几百 KB，装了三十个模块就是十几 MB 常驻 —— 这在低内存
     * 设备上足以让列表在滚动时被系统回收。超过上限就把最久没用的丢掉，读不到只是重新解析一次，
     * 不会出错。
     */
    private const val MAX_CACHED_MODULES = 16

    /** 模块目录里可能存在的横幅文件，顺序即优先级。 */
    private val BANNER_FILES = listOf("banner.png", "banner.jpg", "banner.jpeg", "banner.webp", "banner")

    private val folkStorage by lazy { ModuleBannerStorage(ksuApp, "module_banners") }

    /** 已解析的横幅，键是模块 ID；按最近使用排序，超过上限丢弃最久没用的那个。 */
    private val memory = object : LinkedHashMap<String, ByteArray?>(MAX_CACHED_MODULES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray?>?): Boolean =
            size > MAX_CACHED_MODULES
    }

    /**
     * 同时在途的取图数量上限。
     *
     * 列表一次组合好几张卡片，每张都会发起一次取图；不设上限时滑到一屏新卡片会同时开出十来个
     * 连接，把带宽和线程池都占满 —— 结果是每张都慢，看起来就是「横幅加载不出来」并且列表发卡。
     * 串行一点，先到的先显示，比一起卡着好。
     */
    private val loadSemaphore = Semaphore(permits = 3)

    /** 同一个模块正在取图时，后来的请求等它的结果，而不是再发一次。 */
    private val inFlight = mutableMapOf<String, Deferred<ByteArray?>>()

    /**
     * 取图协程的作用域。
     *
     * 用进程级作用域而不是调用方的：卡片可能因为滚动而离开组合，它的 `produceState` 随之取消，
     * 但已经发出的请求和已下载的字节仍然有用 —— 下一个滚回来的卡片会直接命中缓存。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 丢弃某个模块的缓存（用户手动换了横幅之后调用）。 */
    fun invalidate(moduleId: String) {
        synchronized(memory) { memory.remove(moduleId) }
    }

    fun invalidateAll() {
        synchronized(memory) { memory.clear() }
    }

    /**
     * 取横幅字节；三步都没取到就返回 null，卡片据此不画横幅层。
     *
     * **失败不进缓存。** 只有真取到了才记下来。之前不区分「取到了」和「这次没取到」，于是第一次
     * 取图恰好超时（网络图源是第三方，慢是常态）就把 null 缓存住了，这个模块在本次进程存活期间
     * 再也不会显示横幅 —— 看起来就是「横幅加载不出来」。不缓存失败，下次进列表会再试一次。
     */
    suspend fun load(
        context: Context,
        moduleId: String,
        reload: Boolean = false,
    ): ByteArray? {
        if (!reload && memory.containsKey(moduleId)) {
            return synchronized(memory) { memory[moduleId] }
        }
        // One fetch per module at a time: the card can be asked for its banner again while the
        // first request is still out (a recomposition, a scroll back), and firing a second identical
        // request only made both slower.
        val pending = synchronized(inFlight) { inFlight[moduleId] }
        if (pending != null) return pending.await()

        val job = scope.async {
            loadSemaphore.withPermit {
                val resolved = withContext(Dispatchers.IO) { resolve(context, moduleId) }
                // A null means the fetch did not succeed; leaving it out of the cache lets the next
                // visit try again instead of pinning the card blank for the life of the process.
                if (resolved != null) {
                    synchronized(memory) { memory[moduleId] = resolved }
                }
                resolved
            }
        }
        synchronized(inFlight) { inFlight[moduleId] = job }
        try {
            return job.await()
        } finally {
            synchronized(inFlight) { inFlight.remove(moduleId) }
        }
    }

    /**
     * 同步取一张横幅给 `produceState` 的 initialValue 用，让卡片首帧就有图，不先空一帧。
     *
     * 只碰 `filesDir` 下的本地文件（内存缓存 → API 磁盘缓存 → 指定过的横幅）——**刻意不含
     * 第 3 步「读模块目录」**，那一步要过 root shell，放在主线程会让列表滚动掉帧。
     *
     * 返回 null 是**常态而非异常**：列表一次组合好几张卡片，如果每张都在组合期读一遍磁盘，
     * 滚动就会掉帧。所以这里只读内存缓存，磁盘留给 [load] 在 IO 线程做 —— 卡片晚一两帧出现
     * 横幅，比列表卡一下好。
     */
    fun loadSync(moduleId: String): ByteArray? {
        if (!BackgroundConfig.isBannerEnabled) return null
        // Called during composition, so it must not take a lock that a background fetch holds for
        // long; the fetch only ever swaps the entry in, so a plain read is enough.
        return synchronized(memory) { memory[moduleId] }
    }

    private suspend fun resolve(context: Context, moduleId: String): ByteArray? {
        if (!BackgroundConfig.isBannerEnabled) return null

        // 1. API 模式：源为空或取图失败都继续往下走，而不是直接放弃整个横幅。
        if (BackgroundConfig.isBannerApiModeEnabled) {
            val source = BackgroundConfig.getEffectiveBannerApiSource()
            if (source.isNotBlank()) {
                val api = runCatching {
                    BannerApiService.getModuleBanner(context, moduleId, source)
                }.getOrNull()
                if (api != null) return api
            }
        }

        // 2. 用户自己给这个模块指定过的横幅。
        if (BackgroundConfig.isFolkBannerEnabled) {
            folkStorage.read(moduleId)?.let { return it }
        }

        // 3. 模块目录里自带的横幅。
        return readFromModuleDir(moduleId)
    }

    private fun readFromModuleDir(moduleId: String): ByteArray? {
        if (moduleId.isBlank()) return null

        return runCatching {
            val shell = getRootShell()
            fun su(path: String) = SuFile(path).apply { setShell(shell) }

            val dirPath = "$MODULES_ROOT/$moduleId"
            if (!su(dirPath).isDirectory) return@runCatching null

            // module.prop 的 `banner=` 可以是相对路径；http(s) 地址没法在这里落地成文件，
            // 直接跳过，交给后面的候选文件名。
            val propBanner = runCatching {
                SuFileInputStream.open(su("$dirPath/module.prop")).use { input ->
                    input.bufferedReader()
                        .lineSequence()
                        .firstOrNull { it.startsWith("banner=") }
                        ?.substringAfter('=')
                        ?.trim()
                }
            }.getOrNull()

            val candidates = buildList {
                propBanner
                    ?.takeIf { it.isNotBlank() && !it.startsWith("http", true) }
                    ?.let { add(it) }
                addAll(BANNER_FILES)
            }.distinct()

            candidates.firstNotNullOfOrNull { name ->
                val file = su(if (name.startsWith("/")) name else "$dirPath/$name")
                if (!file.isFile) return@firstNotNullOfOrNull null
                runCatching { SuFileInputStream.open(file).use { it.readBytes() } }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.getOrElse {
            Log.w(TAG, "Failed to read banner for $moduleId: ${it.message}")
            null
        }
    }

    /** 换了 API 源或用户点了「清除缓存」时，把磁盘缓存和内存缓存一起丢掉。 */
    suspend fun clearApiCache(context: Context) {
        BannerApiService.clearAllCache(context)
        invalidateAll()
    }
}
