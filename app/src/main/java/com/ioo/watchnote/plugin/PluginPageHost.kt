package com.ioo.watchnote.plugin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ioo.watchnote.Fish
import com.ioo.watchnote.SubPageListFrame

/**
 * 宿主渲染一个插件页面。
 *
 * 插件只拿到两样东西：要画的内容、以及一个只能 close / open 自己页面的出口；
 * 导航栈仍然在宿主手里，插件关不掉别人的页面，也压不住返回键。
 *
 * 内容页（PageSpec）直接把 content 放进宿主页面壳；列表页（ListPageSpec，4.1.7 起）
 * 交给宿主自己的懒加载列表，条目再多也只渲染屏幕附近那几行。
 *
 * 页面底部如果有这个插件注册的 PAGE_BOTTOM 按钮，宿主把它当成「左下角附加槽位」交给宿主页面壳
 * （Official.Page → SubPageFrame）统一摆放：边距、以及「大返回键升起就飞下去」的升降规则，
 * 都跟主页面那颗小圆按钮一致。4.1.16 之前这里是直接叠在 BottomStart 的死图层：
 * 页面内容不用再滚的时候会出现「左下角小圆钮 + 中间大返回键」并排的画面，看起来像返回键碎了一块。
 * 插件自己画的裸页（没用 Official.Page）不会画这排按钮 —— 屏幕底部是宿主的地盘。
 */
@Composable
fun PluginPageHost(
    core: PluginCore,
    pageId: String,
    arg: Int,
    onClose: () -> Unit,
    onOpen: (pageId: String, arg: Int) -> Unit,
) {
    val spec = core.page(pageId)
    if (spec == null) {
        // 插件被卸载了、页面没了：不要留一张空页，直接退回上一层
        LaunchedEffect(pageId) { onClose() }
        return
    }
    LaunchedEffect(pageId) { core.notifyPageShown(pageId) }
    val scope = remember(pageId) {
        object : PageScope {
            override fun close() { onClose() }
            override fun open(pageId: String, arg: Int) { onOpen(pageId, arg) }
        }
    }
    // 插件 invalidate() 的计数：这里读一下，插件数据变了这一页才会重画（4.1.22）
    val revision = core.revision
    val owner = core.ownerOfPage(pageId)
    val bottomBar: (@Composable () -> Unit)? = if (owner == null) {
        null
    } else {
        { PluginPageBottomBar(core = core, ownerPluginId = owner) }
    }
    Box(Modifier.fillMaxSize()) {
        when (spec) {
            is PageSpec -> CompositionLocalProvider(LocalPluginPageBottomBar provides bottomBar) {
                spec.content(scope, arg)
            }
            is SimplePage -> CompositionLocalProvider(LocalPluginPageBottomBar provides bottomBar) {
                SimplePageContent(spec, scope, revision)
            }
            is ListPageSpec -> SubPageListFrame(onBack = onClose, bottomStart = bottomBar) {
                val lazyScope = this
                val listScope = object : PluginListScope {
                    override fun item(key: Any?, content: @Composable () -> Unit) {
                        lazyScope.item(key = key) { Fish { content() } }
                    }

                    override fun items(count: Int, key: ((Int) -> Any)?, itemContent: @Composable (Int) -> Unit) {
                        repeat(count) { i ->
                            lazyScope.item(key = key?.invoke(i)) { Fish { itemContent(i) } }
                        }
                    }
                }
                spec.content(listScope, scope, arg)
            }
        }
    }
}

/**
 * 声明式页面（SimplePage）的宿主渲染（4.1.21 起）。
 *
 * 插件只交行数据（统计行 / 按钮行），这里一律用官方组件画：
 * 页面壳、标题、返回键、圆屏安全区、表冠滚动、主题色都和内置页一模一样，
 * 插件没有画自由画布的口子，也就不会出现「每个插件一个风格」的花花绿绿。
 */
@Composable
private fun SimplePageContent(page: SimplePage, scope: PageScope, revision: Int) {
    // revision 是插件 invalidate() 的计数：它一变，remember 失效，renderer 重跑一遍拿最新行。
    // 没有 renderer 的页面就是注册时的静态行，行为和 4.1.21 完全一样。
    val snapshot = remember(page, revision) { page.renderOnce() }
    Official.Page(title = page.title, sub = snapshot.subtitle, onBack = { scope.close() }) {
        snapshot.rows.forEach { row -> SimpleRowView(row, page) }
    }
}
