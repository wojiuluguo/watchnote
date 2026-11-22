package com.ioo.watchnote.plugin.builtin

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioo.watchnote.Store
import com.ioo.watchnote.plugin.ButtonSlot
import com.ioo.watchnote.plugin.ButtonSpec
import com.ioo.watchnote.plugin.ListPageSpec
import com.ioo.watchnote.plugin.Official
import com.ioo.watchnote.plugin.PageSpec
import com.ioo.watchnote.plugin.Permission
import com.ioo.watchnote.plugin.PluginHost
import com.ioo.watchnote.plugin.PluginManifest
import com.ioo.watchnote.plugin.WristPlugin

/**
 * 第二个内置插件：笔记统计。
 *
 * 它同时是插件 API 的第一个「真插件」样例：只读笔记（READ_NOTES）、
 * 自己的 kv（记打开次数，落盘）、官方组件（Page/Stat/Button）、
 * 以及从设置页按钮打开自己的页面（host.openPage）。
 * 4.1.6 起再加一颗 PAGE_BOTTOM 圆钮：统计页左下角的「⟳ 刷新」，
 * 让三个按钮插槽都有真实消费者，渲染层不再是「写了没人用」。
 * 4.1.16 起这颗圆钮的位置和升降改由宿主页面壳统一管：大返回键升起（页面内容不用再滚）时
 * 它跟着飞下去，只在页面还能滚的时候才出现在左下角，不会跟大返回键并排成两颗同色圆钮。
 * 4.1.7 起加第三个页面「分类分布」，用 ListPageSpec 写——它是列表页的第一个真实消费者，
 * 分类再多也只渲染屏幕附近那几行，不会一次性把所有行都量一遍。
 *
 * 写这个插件的目的是压 API：一个真实需求能不能只靠 host 给的东西写完？
 * 结果是：需要「整页外壳」和「只读统计行」两个官方组件，已经补进 Official。
 */
class StatsPlugin(private val store: Store) : WristPlugin {

    override val manifest = PluginManifest(
        id = PLUGIN_ID,
        name = "笔记统计",
        version = 1,
        permissions = setOf(Permission.READ_NOTES),
    )

    override fun install(host: PluginHost) {
        host.pages.add(
            PageSpec(id = PAGE_STATS, title = "笔记统计") { scope, _ ->
                StatsPage(
                    host = host,
                    onClose = { scope.close() },
                    onOpenTags = { scope.open(PAGE_TAGS) },
                )
            }
        )
        host.pages.add(
            ListPageSpec(id = PAGE_TAGS, title = "分类分布") { list, scope, _ ->
                val all = host.data.notes()
                val tags = all
                    .filter { it.tag.isNotBlank() }
                    .groupingBy { it.tag }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value }
                list.item(key = "tags-head") {
                    Official.Title("分类分布", "共 " + tags.size + " 类 · " + all.size + " 条")
                }
                if (tags.isEmpty()) {
                    list.item(key = "tags-empty") { Official.Stat("还没有分类", "—") }
                } else {
                    tags.forEach { (tag, count) ->
                        list.item(key = "tag-" + tag) { Official.Stat(tag, count.toString()) }
                    }
                }
                list.item(key = "tags-back") {
                    Official.Button("←", "回到统计", onClick = { scope.close() })
                }
            }
        )
        host.buttons.add(
            ButtonSpec(
                id = manifest.id + ":open",
                slot = ButtonSlot.SETTINGS_ROW,
                icon = "▤",
                label = "笔记统计",
                onClick = { host.openPage(PAGE_STATS) },
            )
        )
        // 第二个插槽的样例：统计页左下角再放一颗刷新圆钮（PAGE_BOTTOM 宿主会叠在页面内容上）
        host.buttons.add(
            ButtonSpec(
                id = manifest.id + ":refresh",
                slot = ButtonSlot.PAGE_BOTTOM,
                icon = "⟳",
                label = "刷新统计",
                onClick = { host.invalidate() },
            )
        )
    }

    override fun uninstall() {
        // 页面/按钮/订阅由宿主按 pluginId 兜底清；这里没有额外资源要收
    }

    @Composable
    private fun StatsPage(
        host: PluginHost,
        onClose: () -> Unit,
        onOpenTags: () -> Unit,
    ) {
        val notes = host.data.notes()
        val done = notes.count { it.done }
        val todo = notes.count { it.todo }
        val tags = notes
            .filter { it.tag.isNotBlank() }
            .groupingBy { it.tag }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key to it.value }

        // 打开次数存在插件自己的 kv 里：证明插件数据会落盘，重启还在
        val opens = remember { host.data.kv().getInt(KEY_OPENS, 0) }
        LaunchedEffect(Unit) { host.data.kv().putInt(KEY_OPENS, opens + 1) }

        Official.Page(
            title = "笔记统计",
            sub = "共 " + notes.size + " 条 · 第 " + (opens + 1) + " 次打开",
            onBack = onClose,
        ) {
            Official.Stat("全部笔记", notes.size.toString())
            Official.Stat("已完成", done.toString())
            Official.Stat("待办", todo.toString())
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "分类分布",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                tags.forEach { (tag, count) -> Official.Stat(tag, count.toString()) }
            }
            Spacer(Modifier.height(8.dp))
            Official.Button("▤", "全部分类", onClick = onOpenTags)
            Spacer(Modifier.height(4.dp))
            Official.Button("⟳", "刷新", onClick = { host.invalidate() })
        }
    }

    companion object {
        const val PLUGIN_ID = "com.ioo.watchnote.stats"
        const val PAGE_STATS = PLUGIN_ID + ":stats"
        const val PAGE_TAGS = PLUGIN_ID + ":tags"
        private const val KEY_OPENS = "opens"
    }
}
