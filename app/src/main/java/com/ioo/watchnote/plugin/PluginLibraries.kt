package com.ioo.watchnote.plugin

import com.ioo.watchnote.EventLog

/**
 * 插件眼里的「前置模块查询口」（0.3 起）。
 *
 * 前置模块的类会被并进依赖它的插件的 classpath，所以插件可以直接 new/调静态方法；
 * 但**同名**的情况必须先问这里：如果前置模块提供的某个符号和宿主同名，那一个符号就被关掉了，
 * 插件应该降级（比如改用官方组件），而不是整个插件不可用。
 */
interface LibraryApi {
    /** 前置模块装没装（且当前是启用状态）。 */
    fun installed(libId: String): Boolean

    /** 已安装前置模块的版本，没装是 0。 */
    fun version(libId: String): Int

    /** 这个符号能不能用：false = 没提供，或者和宿主同名被关掉了。 */
    fun available(libId: String, symbol: String): Boolean

    /** 被关掉的符号（和宿主同名的那几个），给插件做降级判断用。 */
    fun blocked(libId: String): List<String>
}

/** 一个已注册前置模块的公开信息。 */
data class LibraryInfo(
    val id: String,
    val version: Int,
    val provides: List<String>,
    /** 和宿主同名、因此被关掉的符号；其余 provides 仍然可用。 */
    val blocked: List<String>,
)

/**
 * 前置模块注册表（0.3 起）。
 *
 * 关键规则（就是用户要求的那条「保底」）：
 * **冲突按单个符号处理，不按模块处理。** 一个前置模块提供了 8 个类，其中 1 个和宿主同名，
 * 只有那 1 个被关掉，另外 7 个照常可用，依赖它的插件也照常安装。
 *
 * 判定方式很直接：宿主自己的类加载器里已经有的类名 = 这个名字被官方占了。
 * 因为 Android 的类加载是父优先（parent-first），前置模块里同名的类本来也覆盖不了宿主，
 * 与其让插件在运行时莫名其妙拿到官方的类，不如提前把它标成「已关闭」，插件才有机会降级。
 */
class PluginLibraries {

    private val map = LinkedHashMap<String, LibraryInfo>()

    fun register(id: String, version: Int, provides: List<String>) {
        val blocked = provides.filter { symbol -> hostHasClass(symbol) }
        map[id] = LibraryInfo(id, version, provides, blocked)
        if (blocked.isNotEmpty()) {
            EventLog.add(
                "plugin",
                "前置模块 " + id + " 有 " + blocked.size + " 个符号和宿主同名，已单独关闭：" +
                    blocked.joinToString("、") + "（其余 " + (provides.size - blocked.size) + " 个照常可用）",
            )
        }
    }

    fun remove(id: String) { map.remove(id) }

    fun info(id: String): LibraryInfo? = map[id]

    fun all(): List<LibraryInfo> = map.values.toList()

    fun api(): LibraryApi = object : LibraryApi {
        override fun installed(libId: String): Boolean = map.containsKey(libId)
        override fun version(libId: String): Int = map[libId]?.version ?: 0
        override fun available(libId: String, symbol: String): Boolean {
            val info = map[libId] ?: return false
            return info.provides.contains(symbol) && !info.blocked.contains(symbol)
        }
        override fun blocked(libId: String): List<String> = map[libId]?.blocked ?: emptyList()
    }

    private fun hostHasClass(symbol: String): Boolean =
        try {
            Class.forName(symbol, false, PluginLibraries::class.java.classLoader)
            true
        } catch (t: Throwable) {
            false
        }
}
