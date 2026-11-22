package com.ioo.watchnote.plugin

import android.content.Context

/**
 * 插件开关的落盘状态。默认值由 BuiltinPlugins 的 defaultEnabled 决定（多数默认开，改主界面观感的默认关）；
 * 关掉的插件下次启动不会被 install。
 *
 * 单独一个 SharedPreferences 文件，不跟笔记数据混在一起：
 * 用户数据（笔记、时间表、主题色）永远在 watchnote_compose 里，插件状态丢了也不影响数据。
 */
class PluginPrefs(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("watchnote_plugins", Context.MODE_PRIVATE)

    fun isEnabled(pluginId: String): Boolean =
        prefs.getBoolean("enabled_" + pluginId, BuiltinPlugins.find(pluginId)?.defaultEnabled ?: true)

    fun setEnabled(pluginId: String, enabled: Boolean) {
        if (enabled) prefs.edit().remove("enabled_" + pluginId).apply()
        else prefs.edit().putBoolean("enabled_" + pluginId, false).apply()
    }

    /** 现在应该装上的内置插件 id（按目录顺序）。 */
    fun enabledIds(): List<String> = BuiltinPlugins.all.map { it.id }.filter { isEnabled(it) }
}
