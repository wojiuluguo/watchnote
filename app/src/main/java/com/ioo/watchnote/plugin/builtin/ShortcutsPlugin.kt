package com.ioo.watchnote.plugin.builtin

import com.ioo.watchnote.plugin.ButtonSlot
import com.ioo.watchnote.plugin.ButtonSpec
import com.ioo.watchnote.plugin.HostScreen
import com.ioo.watchnote.plugin.Official
import com.ioo.watchnote.plugin.PageSpec
import com.ioo.watchnote.plugin.PluginHost
import com.ioo.watchnote.plugin.PluginManifest
import com.ioo.watchnote.plugin.WristPlugin

/**
 * 第四个内置试点：官方页面引用（快捷键 / 官方入口）。
 *
 * 这一页的每一行都不是自己画的，而是「引用官方页面」：插件只交一个 HostScreen 枚举值，
 * 宿主负责打开自己那一份页面。插件不需要知道那个页面长什么样、怎么返回、怎么适配圆屏——
 * 这正是「有些按钮直接引用，非常方便」那条 API 的样板。
 *
 * 默认关闭：它会往设置页多加一行，属于会改主界面观感的插件，要先在插件管理页手动打开。
 */
class ShortcutsPlugin : WristPlugin {

    override val manifest: PluginManifest = PluginManifest(
        id = PLUGIN_ID,
        name = "快捷入口",
        version = 1,
    )

    override fun install(host: PluginHost) {
        host.pages.add(
            PageSpec(id = PAGE_HOME, title = "快捷入口") { scope, _ ->
                Official.Page("快捷入口", "官方页面的入口", onBack = { scope.close() }) {
                    Official.ScreenRow(host, HostScreen.MANAGE, "≡")
                    Official.ScreenRow(host, HostScreen.SPECIAL_LIST, "◷")
                    Official.ScreenRow(host, HostScreen.SHARE_TO_PHONE, "⇪")
                    Official.ScreenRow(host, HostScreen.PLUGIN_MANAGER, "⌘")
                    Official.ScreenRow(host, HostScreen.ABOUT, "i")
                    Official.ScreenRow(host, HostScreen.SELF_TEST, "⚙")
                }
            }
        )
        host.buttons.add(
            ButtonSpec(
                id = PLUGIN_ID + ":open",
                slot = ButtonSlot.SETTINGS_ROW,
                icon = "→",
                label = "快捷入口",
                onClick = { host.openPage(PAGE_HOME) },
            )
        )
    }

    override fun uninstall() {
        // 页面和按钮都由宿主按 pluginId 兜底清，这里不用做什么
    }

    companion object {
        const val PLUGIN_ID = "com.ioo.watchnote.shortcuts"
        const val PAGE_HOME = PLUGIN_ID + ":home"
    }
}
