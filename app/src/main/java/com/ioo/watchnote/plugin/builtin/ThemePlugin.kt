package com.ioo.watchnote.plugin.builtin

import androidx.compose.runtime.Composable
import com.ioo.watchnote.ColorListPage
import com.ioo.watchnote.ColorWheelPage
import com.ioo.watchnote.Store
import com.ioo.watchnote.UiPrefsState
import com.ioo.watchnote.plugin.PageSpec
import com.ioo.watchnote.plugin.PluginHost
import com.ioo.watchnote.plugin.PluginManifest
import com.ioo.watchnote.plugin.Permission
import com.ioo.watchnote.plugin.WristPlugin

/**
 * 第一个试点插件：把「设置 → 主题颜色」整页搬进插件体系。
 *
 * 它只做两件事：
 * 1. 注册一个列表页（5 个预设色 + 15 个中国传统色系）
 * 2. 注册一个滚轮选色页，从列表页带一个 arg（第几个色系）进去
 *
 * 它不知道宿主导航栈长什么样，只能 scope.close() / scope.open()，
 * 所以「关掉插件后这一整块消失、颜色设置还在」是天然成立的。
 */
class ThemePlugin(
    private val store: Store,
    private val onColorChanged: () -> Unit,
) : WristPlugin {

    companion object {
        const val PLUGIN_ID = "com.ioo.watchnote.theme"
        const val PAGE_LIST = PLUGIN_ID + ":list"
        const val PAGE_WHEEL = PLUGIN_ID + ":wheel"
    }

    override val manifest: PluginManifest = PluginManifest(
        id = PLUGIN_ID,
        name = "主题颜色",
        version = 1,
        permissions = setOf(Permission.READ_SETTINGS),
    )

    override fun install(host: PluginHost) {
        host.pages.add(
            PageSpec(
                id = PAGE_LIST,
                title = "主题颜色",
                content = { scope, _ ->
                    ColorListPage(
                        currentPreset = UiPrefsState.accent.value,
                        customArgb = UiPrefsState.customAccent.value,
                        onPreset = {
                            store.setAccent(it)
                            onColorChanged()
                        },
                        onOpenSeries = { index -> scope.open(PAGE_WHEEL, index) },
                        onBack = { scope.close() },
                    )
                },
            )
        )
        host.pages.add(
            PageSpec(
                id = PAGE_WHEEL,
                title = "选颜色",
                content = { scope, arg ->
                    ColorWheelPage(
                        seriesIndex = arg,
                        selectedArgb = UiPrefsState.customAccent.value,
                        onPick = { argb, name ->
                            store.setAccentColor(argb, name)
                            onColorChanged()
                            scope.close()
                        },
                        onBack = { scope.close() },
                    )
                },
            )
        )
    }

    override fun uninstall() {
        // 宿主按 pluginId 兜底清页面，这里不用手写清理；颜色设置是用户数据，不随插件走。
    }
}
