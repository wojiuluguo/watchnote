package com.ioo.watchnote.plugin

import android.content.Context

/**
 * 插件私有数据的落盘实现：一个插件一个 SharedPreferences 文件，
 * 文件名叫 watchnote_plugin_<pluginId>，跟笔记数据（watchnote_compose）完全分开。
 *
 * 为什么一个插件一个文件：清某一个插件的数据只要 clear 它自己，
 * 不会碰到别的插件，也不会因为 kv 结构变化冒风险动用户笔记。
 */
class PrefKeyValueStore(ctx: Context, pluginId: String) : KeyValueStore {

    private val prefs = ctx.getSharedPreferences("watchnote_plugin_" + pluginId, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun keys(): Set<String> = prefs.all.keys.toSet()

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
