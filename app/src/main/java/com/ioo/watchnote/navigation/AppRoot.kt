package com.ioo.watchnote

import com.ioo.watchnote.plugin.BuiltinPlugins
import com.ioo.watchnote.plugin.HostScreen
import com.ioo.watchnote.plugin.PluginCore
import com.ioo.watchnote.plugin.PluginManagerPage
import com.ioo.watchnote.plugin.PluginPrefs
import com.ioo.watchnote.plugin.PrefKeyValueStore
import com.ioo.watchnote.plugin.RegisterResult
import com.ioo.watchnote.plugin.external.ExternalPluginInstaller
import com.ioo.watchnote.plugin.PluginPageHost
import com.ioo.watchnote.plugin.builtin.ThemePlugin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ScreenScaffold
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxScope
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.ceil

/* ============================================================
   数据 / 导航
   ============================================================ */

data class ConfirmData(
    val title: String,
    val message: String,
    val okLabel: String,
    val danger: Boolean,
    val onOk: () -> Unit
)

sealed interface SubPage {
    data class PluginPage(val pageId: String, val arg: Int = 0) : SubPage
    object Manage : SubPage
    object About : SubPage
    object AnimMode : SubPage
    object SelfTest : SubPage
    object Plugins : SubPage
    object Share : SubPage
    object OpenSource : SubPage
    object GroupList : SubPage
    data class GroupEdit(val id: String) : SubPage
    data class GroupItemEdit(val groupId: String, val index: Int) : SubPage
    object SpecialList : SubPage
    data class SpecialEdit(val id: String) : SubPage
    data class SpecialItemEdit(val ruleId: String, val index: Int) : SubPage
    data class SlotEdit(val id: String) : SubPage
    data class NoteEdit(val id: String) : SubPage
    object TodoTime : SubPage
    data class TodoEdit(val noteId: String = "", val start: Long = 0, val end: Long = 0) : SubPage
    data class View(val noteId: String) : SubPage
    data class More(val noteId: String) : SubPage
}

@Composable
fun AppRoot(activity: MainActivity, store: Store) {
    var refresh by remember { mutableIntStateOf(0) }
    store.version.intValue
    var stack by remember { mutableStateOf<List<SubPage>>(emptyList()) }
    BackHandler(enabled = stack.isNotEmpty()) {
        stack = stack.dropLast(1)
    }
    var confirm by remember { mutableStateOf<ConfirmData?>(null) }
    val animMode = UiPrefsState.animMode.value
    val scope = rememberCoroutineScope()
    // 传 Context：插件私有 kv 落盘；自测用的沙盒核心不传，就是纯内存
    val pluginCore = remember { PluginCore(store, kvFactory = { id -> PrefKeyValueStore(activity, id) }) }
    val pluginPrefs = remember { PluginPrefs(activity) }
    // 外置插件加载器：包结构检查 / 解压 / DexClassLoader / 记录，全在这里
    val externalInstaller = remember { ExternalPluginInstaller(activity.applicationContext, pluginCore) }
    pluginCore.toastHandler = { activity.toast(it) }
    pluginCore.invalidateHandler = { refresh++ }
    // 插件点按钮要开自己的页面时，走宿主这套栈，插件碰不到栈本身
    pluginCore.navigateHandler = { pageId -> stack = stack + SubPage.PluginPage(pageId) }
    // 插件引用官方页面（HostScreen 白名单）：这里只负责把枚举翻成宿主自己的页面
    pluginCore.hostNavigateHandler = { screen ->
        EventLog.add("plugin", "open host screen " + screen.name)
        stack = stack + when (screen) {
            HostScreen.ABOUT -> SubPage.About
            HostScreen.SELF_TEST -> SubPage.SelfTest
            HostScreen.OPEN_SOURCE -> SubPage.OpenSource
            HostScreen.PLUGIN_MANAGER -> SubPage.Plugins
            HostScreen.MANAGE -> SubPage.Manage
            HostScreen.SPECIAL_LIST -> SubPage.SpecialList
            HostScreen.SHARE_TO_PHONE -> SubPage.Share
            HostScreen.ANIM_MODE -> SubPage.AnimMode
        }
    }
    // 读快照：插件装/卸之后，设置页的「插件」行和「更换主题颜色」行都跟着变
    val pluginCount = pluginCore.manifests.size
    val themeInstalled = pluginCore.isInstalled(ThemePlugin.PLUGIN_ID)
    LaunchedEffect(Unit) {
        BuiltinPlugins.all.forEach { entry ->
            if (pluginPrefs.isEnabled(entry.id)) {
                val result = pluginCore.install(entry.build(store) { refresh++ })
                if (result is RegisterResult.Rejected) {
                    EventLog.add("plugin", "install " + entry.id + " 失败：" + result.reason)
                }
            }
        }
        // 外置插件：按上次装好的记录重新加载（不重新解压）
        val restored = externalInstaller.restoreAll()
        if (restored > 0) {
            EventLog.add("plugin", "恢复外置插件 " + restored + " 个")
            refresh++
        }
    }
    val pager = rememberPagerState(initialPage = 2000) { 4000 }
    val fullAnim = animMode == 0
    var hapticFirst by remember { mutableStateOf(true) }
    LaunchedEffect(pager.currentPage) {
        val idx = (((pager.currentPage - 2000) % 5) + 5) % 5
        EventLog.add("page", "switch page=" + pager.currentPage + " idx=" + idx)
        if (hapticFirst) hapticFirst = false else Haptics.page()
    }
    LaunchedEffect(stack) {
        EventLog.add("nav", "stack=" + stack.joinToString(">"))
    }
    BackHandler(enabled = stack.isEmpty()) {
        val idx = (((pager.currentPage - 2000) % 5) + 5) % 5
        if (idx == 0) {
            confirm = ConfirmData("退出应用", "要退出手表笔记吗？", "退出", true) { activity.finish() }
        } else {
            scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when ((((page - 2000) % 5) + 5) % 5) {
                0 -> NotesPage(
                    store = store,
                    refresh = refresh,
                    fullAnim = fullAnim,
                    active = page == pager.currentPage,
                    onAdd = { stack = stack + SubPage.NoteEdit("") },
                    onOpen = { stack = stack + SubPage.View(it) },
                    onChanged = { refresh++ },
                    pluginCore = pluginCore
                )
                1 -> TodoPage(
                    store = store,
                    refresh = refresh,
                    fullAnim = fullAnim,
                    active = page == pager.currentPage,
                    onAdd = { stack = stack + SubPage.TodoTime },
                    onOpen = { stack = stack + SubPage.View(it) },
                    onChanged = { refresh++ }
                )
                2 -> TimetablePage(
                    store = store,
                    refresh = refresh,
                    fullAnim = fullAnim,
                    active = page == pager.currentPage,
                    onManage = { stack = stack + SubPage.Manage },
                    onEdit = { stack = stack + SubPage.SlotEdit(it) }
                )
                3 -> ClockPage(store = store, refresh = refresh, fullAnim = fullAnim)
                else -> SettingsPage(
                    store = store,
                    onShowTopTime = { store.setShowTopTime(!UiPrefsState.showTopTime.value) },
                    active = page == pager.currentPage,
                    onBackToNotes = { scope.launch { pager.animateScrollToPage(2000) } },
                    onAnimMode = {
                        EventLog.add("setting", "open anim mode page")
                        stack = stack + SubPage.AnimMode
                    },
                    onManage = { stack = stack + SubPage.Manage },
                    onSpecial = { stack = stack + SubPage.SpecialList },
                    onReset = {
                        confirm = ConfirmData("恢复默认", "恢复默认时间表？\n自定义的时段会被覆盖。", "恢复", false) {
                            store.resetSchedule(); refresh++
                        }
                    },
                    onExport = {
                        val path = activity.exportBackup()
                        activity.toast(if (path != null) "备份已保存" else "导出失败")
                    },
                    onImport = {
                        confirm = ConfirmData("从备份恢复", "从 watchnote-backup.json 恢复？\n当前笔记与时间表会被覆盖。", "恢复", false) {
                            val raw = activity.importBackup()
                            if (raw == null) activity.toast("没有找到备份文件")
                            else if (store.importJson(raw)) { refresh++; activity.toast("已从备份恢复") }
                            else activity.toast("备份文件内容无效")
                        }
                    },
                    onShare = { stack = stack + SubPage.Share },
                    onClear = {
                        confirm = ConfirmData("清空数据", "清空全部数据？\n笔记与时间表都会恢复初始状态。", "清空", true) {
                            store.resetAll(); refresh++
                        }
                    },
                    onTheme = { stack = stack + SubPage.PluginPage(ThemePlugin.PAGE_LIST) },
                    onAbout = {
                        EventLog.add("setting", "open about page")
                        stack = stack + SubPage.About
                    },
                    pluginCore = pluginCore,
                    themeInstalled = themeInstalled,
                    pluginCount = pluginCount,
                    onPlugins = { stack = stack + SubPage.Plugins }
                )
            }
        }
        PageDots(pager, Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
        val pageIdx = (((pager.currentPage - 2000) % 5) + 5) % 5
        if (UiPrefsState.showTopTime.value && pageIdx != 3) {
            LaunchedEffect(pageIdx) { EventLog.add("ui", "overlay topTime shown page=" + pageIdx) }
            TimeText(modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
        }
    }

    val top = stack.lastOrNull()
    AnimatedContent(
        targetState = top,
        transitionSpec = {
            val d = if (fullAnim) 220 else 0
            (slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(d)) + fadeIn(tween(d))) togetherWith
                (slideOutVertically(targetOffsetY = { -it / 3 }, animationSpec = tween(if (fullAnim) 160 else 0)) + fadeOut(tween(if (fullAnim) 160 else 0)))
        },
        label = "subpage"
    ) { page ->
        when (page) {
        SubPage.Plugins -> PluginManagerPage(
            core = pluginCore,
            store = store,
            prefs = pluginPrefs,
            installer = externalInstaller,
            onBack = { stack = stack.dropLast(1) },
            onChanged = { refresh++ },
            onOpenPage = { pageId -> stack = stack + SubPage.PluginPage(pageId) }
        )
        is SubPage.PluginPage -> PluginPageHost(
            core = pluginCore,
            pageId = page.pageId,
            arg = page.arg,
            onClose = { stack = stack.dropLast(1) },
            onOpen = { pageId, arg -> stack = stack + SubPage.PluginPage(pageId, arg) }
        )
        SubPage.Manage -> ManagePage(
            store = store,
            refresh = refresh,
            onBack = { stack = stack.dropLast(1) },
            onAdd = { stack = stack + SubPage.SlotEdit("") },
            onEdit = { stack = stack + SubPage.SlotEdit(it) },
            onGroups = { stack = stack + SubPage.GroupList }
        )
        SubPage.GroupList -> GroupListPage(
            store = store,
            onBack = { stack = stack.dropLast(1) },
            onAdd = {
                val g = store.newGroup()
                stack = stack + SubPage.GroupEdit(g.id)
            },
            onEdit = { stack = stack + SubPage.GroupEdit(it) }
        )
        is SubPage.GroupEdit -> GroupEditPage(
            store = store,
            groupId = page.id,
            onBack = { stack = stack.dropLast(1) },
            onSaved = { stack = stack.dropLast(1) },
            onEditItem = { i -> stack = stack + SubPage.GroupItemEdit(page.id, i) }
        )
        is SubPage.GroupItemEdit -> GroupItemEditPage(
            store = store,
            groupId = page.groupId,
            index = page.index,
            onBack = { stack = stack.dropLast(1) }
        )
        SubPage.SpecialList -> SpecialListPage(
            store = store,
            onBack = { stack = stack.dropLast(1) },
            onAdd = {
                val r = store.newSpecial(store.currentWeekdayIdx())
                stack = stack + SubPage.SpecialEdit(r.id)
            },
            onEdit = { stack = stack + SubPage.SpecialEdit(it) }
        )
        is SubPage.SpecialEdit -> SpecialEditPage(
            store = store,
            ruleId = page.id,
            onBack = { stack = stack.dropLast(1) },
            onSaved = { stack = stack.dropLast(1) },
            onEditItem = { i -> stack = stack + SubPage.SpecialItemEdit(page.id, i) }
        )
        is SubPage.SpecialItemEdit -> SpecialItemEditPage(
            store = store,
            ruleId = page.ruleId,
            index = page.index,
            onBack = { stack = stack.dropLast(1) }
        )
        SubPage.About -> AboutPage(
            onBack = { stack = stack.dropLast(1) },
            onOpenSelfTest = { stack = stack + SubPage.SelfTest },
            onOpenSource = { stack = stack + SubPage.OpenSource }
        )
        SubPage.OpenSource -> OpenSourcePage(
            onBack = { stack = stack.dropLast(1) },
            onCopy = { activity.copyText(PROJECT_URL) }
        )
        SubPage.SelfTest -> SelfTestPage(
            store = store,
            externalSelfTest = { externalInstaller.selfTestExternalLoad() },
            externalSelfTestInstall = { externalInstaller.selfTestExternalInstall() },
            onExport = {
                val p = activity.exportAppLog()
                activity.toast(if (p != null) "日志已保存：" + p else "导出失败")
            },
            onCopy = { activity.copyText(EventLog.dump()) },
            onClear = {
                EventLog.clear()
                activity.toast("日志已清空")
            },
            onBack = { stack = stack.dropLast(1) }
        )
        SubPage.Share -> SharePage(
            store = store,
            onBack = { stack = stack.dropLast(1) }
        )
        SubPage.AnimMode -> AnimModePage(
            current = animMode,
            onSelect = { store.setAnimMode(it); refresh++ },
            onBack = { stack = stack.dropLast(1) }
        )
        is SubPage.SlotEdit -> SlotEditPage(
            activity = activity,
            store = store,
            slotId = page.id,
            onBack = { stack = stack.dropLast(1) },
            onSaved = { refresh++ }
        )
        is SubPage.View -> NoteViewPage(
            activity = activity,
            store = store,
            noteId = page.noteId,
            onBack = { stack = stack.dropLast(1) },
            onMore = { stack = stack + SubPage.More(it) }
        )
        is SubPage.More -> NoteMorePage(
            activity = activity,
            store = store,
            noteId = page.noteId,
            onBack = { stack = stack.dropLast(1) },
            onEdit = { id ->
                val n = store.getNote(id)
                stack = stack + if (n != null && n.todo) SubPage.TodoEdit(id) else SubPage.NoteEdit(id)
            },
            onGone = { stack = stack.dropLast(2) }
        )
        SubPage.TodoTime -> TodoTimePage(
            store = store,
            onBack = { stack = stack.dropLast(1) },
            // 替换掉「定时间」这一层：保存后直接回办事列表，不再回到定时间页
            onNext = { start, end -> stack = stack.dropLast(1) + SubPage.TodoEdit("", start, end) }
        )
        is SubPage.TodoEdit -> TodoEditPage(
            store = store,
            noteId = page.noteId,
            presetStart = page.start,
            presetEnd = page.end,
            onBack = { stack = stack.dropLast(1) },
            onChanged = { refresh++ }
        )
        is SubPage.NoteEdit -> NoteEditPage(
            activity = activity,
            store = store,
            noteId = page.id,
            onBack = { stack = stack.dropLast(1) },
            onChanged = { refresh++ }
        )
            null -> {}
        }
    }

    confirm?.let { c -> ConfirmDialog(c) { confirm = null } }
}