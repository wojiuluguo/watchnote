package com.ioo.watchnote.plugin.builtin

import com.ioo.watchnote.plugin.DrawBlock
import com.ioo.watchnote.plugin.Permission
import com.ioo.watchnote.plugin.PluginHost
import com.ioo.watchnote.plugin.PluginManifest
import com.ioo.watchnote.plugin.UIPage
import com.ioo.watchnote.plugin.Ui
import com.ioo.watchnote.plugin.WristPlugin

/**
 * 插件模块 0.4.5 的活样板，也是这一版的现场自测：
 *
 * - 容器树：分组、横排、内边距、按权重分宽；
 * - 元素画布：环形进度 + 点击改数值，跟官方行同层；
 * - 整页画布：第二页整页自绘表盘，点哪画哪；
 * - 联网：host.net() 发一次真请求，失败也把原因显示出来；
 * - 插件主页面：asMainPage() —— 打开它，左边指示栏会从 5 格变 6 格。
 *
 * 默认关闭：会改指示栏形制，属于会动主界面观感的插件，去「插件管理 → 官方插件」手动打开。
 */
class CanvasDemoPlugin : WristPlugin {

    override val manifest: PluginManifest = PluginManifest(
        id = PLUGIN_ID,
        name = "画布演示",
        version = 1,
        permissions = setOf(Permission.NET),
    )

    private var pct: Float = 0.35f
    private var netText: String = "还没发过请求"
    private var hitX: Float = 0.5f
    private var hitY: Float = 0.5f

    override fun install(host: PluginHost) {
        val home = UIPage(PAGE_HOME, "画布演示").asMainPage().sub("容器树 · 画布 · 联网")
        home.add(
            Ui.group(
                "容器树",
                Ui.stat("布局", "官方行 + 画布"),
                Ui.note("下面这块是画布，不是图片"),
            ),
            Ui.canvas(58, DrawBlock { pen ->
                pen.arc(0.5f, 0.5f, 0.42f, -90f, 360f, "muted", strokeDp = 7f)
                pen.arc(0.5f, 0.5f, 0.42f, -90f, 360f * pct, "primary", strokeDp = 7f)
                pen.text((pct * 100).toInt().toString() + "%", 0.5f, 0.5f, sizeDp = 14f, bold = true)
            }).onTap { _, _ ->
                bump()
                host.invalidate()
            },
            Ui.row(
                8,
                Ui.button("＋10%", "bump"),
                Ui.button("整页画布", "full"),
            ),
            Ui.section("联网"),
            Ui.stat("上次结果", netText),
            Ui.button("发一次请求", "net"),
            Ui.note("要联网权限；没网时这里会写原因，不是崩"),
        )
        home.onAction { id ->
            when (id) {
                "bump" -> {
                    bump()
                    host.invalidate()
                }
                "net" -> {
                    netText = "请求中…"
                    host.invalidate()
                    host.net.get("https://api.github.com/zen") { r ->
                        netText = if (r.ok) r.body.trim().take(40) else (r.error ?: ("HTTP " + r.code))
                        host.invalidate()
                    }
                }
                "full" -> host.openPage(PAGE_FULL)
            }
        }
        host.pages.add(home)

        val full = UIPage(PAGE_FULL, "整页画布").fullPage()
        full.add(
            Ui.fillCanvas(DrawBlock { pen ->
                pen.clear("card")
                pen.circle(0.5f, 0.5f, 0.46f, "muted", strokeDp = 2f)
                val a = angle()
                pen.line(
                    0.5f, 0.5f,
                    0.5f + 0.34f * kotlin.math.cos(a).toFloat(),
                    0.5f + 0.34f * kotlin.math.sin(a).toFloat(),
                    "primary", widthDp = 3f,
                )
                pen.circle(hitX, hitY, 0.06f, "danger")
                pen.text("整页自绘", 0.5f, 0.16f, sizeDp = 13f, colorHex = "muted")
            }).onTap { x, y ->
                hitX = x
                hitY = y
                host.invalidate()
            },
        )
        host.pages.add(full)
    }

    override fun uninstall() {
        // 页面由宿主按 pluginId 兜底清
    }

    private fun bump() {
        pct += 0.1f
        if (pct > 1f) pct = 0.1f
    }

    private fun angle(): Float = (System.currentTimeMillis() % 60000L) / 60000f * 6.283f

    companion object {
        const val PLUGIN_ID = "com.ioo.watchnote.canvasshow"
        const val PAGE_HOME = PLUGIN_ID + ":home"
        const val PAGE_FULL = PLUGIN_ID + ":full"
    }
}
