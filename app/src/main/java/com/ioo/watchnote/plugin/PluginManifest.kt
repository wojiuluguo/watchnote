package com.ioo.watchnote.plugin

/** 宿主 API 版本。插件写 minApi，宿主拿它做准入检查，避免老宿主跑新插件直接崩。 */
object PluginApi {
    const val API_LEVEL: Int = 1

    /** 插件模块自身的版本（关于 → 详细信息里显示）。API 还在测试阶段，所以是 0.x。 */
    const val MODULE_VERSION: String = "0.1"
}

/** v1 开放的权限。没申请的权限，DataApi 会直接拒绝调用。 */
enum class Permission {
    READ_NOTES,
    WRITE_NOTES,
    READ_SETTINGS,
}

/**
 * 插件身份证。
 * id 用小写字母、数字、点，例：com.ioo.watchnote.theme
 * 宿主用 id 做命名空间（页面 id 必须以此开头）和卸载时的清理键。
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: Int,
    val minApi: Int = PluginApi.API_LEVEL,
    val permissions: Set<Permission> = emptySet(),
)
