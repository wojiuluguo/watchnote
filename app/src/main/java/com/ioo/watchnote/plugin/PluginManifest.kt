package com.ioo.watchnote.plugin

/** 宿主 API 版本。插件写 minApi，宿主拿它做准入检查，避免老宿主跑新插件直接崩。 */
object PluginApi {
    const val API_LEVEL: Int = 5

    /** 插件模块自身的版本（关于 → 详细信息里显示）。API 还在测试阶段，所以是 0.x。 */
    const val MODULE_VERSION: String = "0.4.5"
}

/** v1 开放的权限。没申请的权限，DataApi 会直接拒绝调用。 */
enum class Permission {
    READ_NOTES,
    WRITE_NOTES,
    READ_SETTINGS,

    /** 0.4.5 起：走宿主 host.net() 联网。不声明这个权限，联网调用一律被拒。 */
    NET,
}

/**
 * 插件类型（4.1.29 / 0.3 起）。
 *
 * - [APP]：普通插件，可以注册页面/按钮，也可以依赖前置模块。
 * - [LIB]：**前置模块**（前置插件）。不许注册任何页面或按钮，只把自己的类交给依赖它的插件用，
 *   相当于一个打包好的 SDK：算法、工具、UI 排版助手都可以放在这里。
 */
enum class PluginKind { APP, LIB }

/** 一条前置依赖：需要 id 对应的前置模块，版本至少 minVersion。 */
data class PluginDependency(val id: String, val minVersion: Int = 1) {
    override fun toString(): String = id + ">=" + minVersion
}

/**
 * 插件身份证。
 * id 用小写字母、数字、点，例：com.ioo.watchnote.theme
 * 宿主用 id 做命名空间（页面 id 必须以此开头）和卸载时的清理键。
 *
 * 0.3 起多了三样（全部有默认值，老插件不用改）：
 * - [kind]：普通插件还是前置模块；
 * - [provides]：前置模块对外提供的符号（完整类名），用来和宿主同名类做逐项隔离；
 * - [requires]：本插件依赖哪些前置模块。
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: Int,
    val minApi: Int = PluginApi.API_LEVEL,
    val permissions: Set<Permission> = emptySet(),
    val kind: PluginKind = PluginKind.APP,
    val provides: List<String> = emptyList(),
    val requires: List<PluginDependency> = emptyList(),
)
