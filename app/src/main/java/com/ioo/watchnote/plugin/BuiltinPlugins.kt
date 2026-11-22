package com.ioo.watchnote.plugin

import com.ioo.watchnote.Store
import com.ioo.watchnote.plugin.builtin.NoteInfoPlugin
import com.ioo.watchnote.plugin.builtin.ShortcutsPlugin
import com.ioo.watchnote.plugin.builtin.StatsPlugin
import com.ioo.watchnote.plugin.builtin.ThemePlugin

/**
 * 内置插件目录。以后每写一个新插件，只在这里加一行；安装、开关、管理页全都不用改。
 *
 * build 是延迟创建：只有真要装的时候才 new 插件，没装的插件不占内存、不跑 install。
 * defaultEnabled = false 表示「装好 App 也不默认开」，用户去插件管理页手动打开才生效；
 * 会改动主界面观感的插件（比如往每张笔记卡片上加按钮）必须默认关闭。
 */
data class BuiltinPluginEntry(
    val id: String,
    val name: String,
    val summary: String,
    val build: (Store, () -> Unit) -> WristPlugin,
    val defaultEnabled: Boolean = true,
)

object BuiltinPlugins {

    val all: List<BuiltinPluginEntry> = listOf(
        BuiltinPluginEntry(
            id = ThemePlugin.PLUGIN_ID,
            name = "主题颜色",
            summary = "设置里的「更换主题颜色」整页：15 个色系、526 种中国传统色",
            build = { store, onChanged -> ThemePlugin(store, onChanged) },
        ),
        BuiltinPluginEntry(
            id = StatsPlugin.PLUGIN_ID,
            name = "笔记统计",
            summary = "设置页多一行「笔记统计」：总数、已办、待办、分类分布",
            build = { store, _ -> StatsPlugin(store) },
        ),
        BuiltinPluginEntry(
            id = NoteInfoPlugin.PLUGIN_ID,
            name = "笔记详情",
            summary = "默认关闭：每条笔记卡片上多一个 ⓘ，点开看这条笔记的字数、创建/更新时间",
            build = { store, _ -> NoteInfoPlugin(store) },
            defaultEnabled = false,
        ),
        BuiltinPluginEntry(
            id = ShortcutsPlugin.PLUGIN_ID,
            name = "快捷入口",
            summary = "默认关闭：设置页多一行「快捷入口」，里面全是官方页面的入口（时间表、特殊时段、发到手机、插件、关于、自测）",
            build = { _, _ -> ShortcutsPlugin() },
            defaultEnabled = false,
        ),
    )

    fun find(id: String): BuiltinPluginEntry? = all.firstOrNull { it.id == id }
}
