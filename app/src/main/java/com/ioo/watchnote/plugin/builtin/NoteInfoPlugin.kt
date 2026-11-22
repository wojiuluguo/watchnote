package com.ioo.watchnote.plugin.builtin

import androidx.compose.runtime.Composable
import com.ioo.watchnote.Note
import com.ioo.watchnote.Store
import com.ioo.watchnote.plugin.ButtonSlot
import com.ioo.watchnote.plugin.ButtonSpec
import com.ioo.watchnote.plugin.Official
import com.ioo.watchnote.plugin.PageScope
import com.ioo.watchnote.plugin.PageSpec
import com.ioo.watchnote.plugin.Permission
import com.ioo.watchnote.plugin.PluginHost
import com.ioo.watchnote.plugin.PluginManifest
import com.ioo.watchnote.plugin.WristPlugin

/**
 * 第一个用 CARD_ACTION 的内置插件：给每条笔记卡片加一个 ⓘ，点开看这条笔记的详情。
 *
 * 它同时是 CARD_ACTION 的验收样例：
 * - 按钮必须带 onCardAction —— 点卡片按钮时插件能拿到**那条笔记**，而不是只收到一个「被点了」；
 * - 拿到的 id 存进插件自己的 kv，再 openPage 打开自己的页面（页面参数只有 Int，所以用 kv 传 id）；
 * - 页面只用 Official.* 画，外壳/返回/滚动全部由宿主提供。
 *
 * 默认关闭（BuiltinPlugins 里 defaultEnabled = false）：它改的是笔记卡片的观感，
 * 不能装完就悄悄多一个按钮，必须让用户在「插件管理页」里自己开。
 */
class NoteInfoPlugin(private val store: Store) : WristPlugin {

    companion object {
        const val PLUGIN_ID = "com.ioo.watchnote.noteinfo"
        const val PAGE_INFO = PLUGIN_ID + ":info"
        internal const val KEY_LAST = "lastNoteId"
    }

    override val manifest = PluginManifest(
        id = PLUGIN_ID,
        name = "笔记详情",
        version = 1,
        permissions = setOf(Permission.READ_NOTES),
    )

    override fun install(host: PluginHost) {
        host.buttons.add(
            ButtonSpec(
                id = PLUGIN_ID + ":open",
                slot = ButtonSlot.CARD_ACTION,
                icon = "ⓘ",
                label = "这条笔记的详情",
                onClick = {},
                onCardAction = { note: Note ->
                    host.data.kv().putString(KEY_LAST, note.id)
                    host.openPage(PAGE_INFO)
                },
            )
        )
        host.pages.add(
            PageSpec(
                id = PAGE_INFO,
                title = "笔记详情",
                content = { scope, _ -> NoteInfoPage(host = host, scope = scope) },
            )
        )
    }

    override fun uninstall() {}
}

@Composable
private fun NoteInfoPage(host: PluginHost, scope: PageScope) {
    // 页面参数只有 Int，传不了 Note；所以卡片按钮先把 id 写进 kv，这里再按 id 取回来
    val lastId = host.data.kv().getString(NoteInfoPlugin.KEY_LAST) ?: ""
    val note = host.data.notes().firstOrNull { it.id == lastId }
    if (note == null) {
        Official.Page(title = "笔记详情", sub = "这条笔记已经不在了", onBack = { scope.close() }) {
            Official.Button(symbol = "←", label = "回到笔记", onClick = { scope.close() })
        }
        return
    }
    Official.Page(title = "笔记详情", sub = note.tag, onBack = { scope.close() }) {
        Official.Stat("字数", note.text.trim().length.toString() + " 字")
        Official.Stat("创建", Store.fmtStamp(note.createdAt))
        Official.Stat("更新", Store.fmtStamp(note.updatedAt))
        Official.Stat("完成", if (note.done) "已完成" else "未完成")
        if (note.audioPath.isNotEmpty()) {
            val seconds = Math.max(1, Math.round(note.audioDur / 1000.0))
            Official.Stat("语音", seconds.toString() + " 秒")
        }
        Official.Button(symbol = "←", label = "回到笔记", onClick = { scope.close() })
    }
}
