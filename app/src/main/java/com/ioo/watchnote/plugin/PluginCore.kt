package com.ioo.watchnote.plugin

import androidx.compose.runtime.mutableStateListOf
import com.ioo.watchnote.EventLog
import com.ioo.watchnote.Note
import com.ioo.watchnote.Store

/** 每个插件最多注册几页、每个插槽最多几个按钮 —— 防插件把圆屏塞满。 */
const val MAX_PAGES_PER_PLUGIN = 3
const val MAX_BUTTONS_PER_SLOT = 2

/** 外部插件连续出错几次就自动停用 —— 坏插件不能一直拖累宿主。 */
const val MAX_PLUGIN_FAILURES = 3

/** 已注册页面/按钮都带 owner，卸载时按 owner 一把清干净，不留残影。 */
data class PluginPage(val ownerPluginId: String, val spec: PluginPageSpec)
data class PluginButton(val ownerPluginId: String, val spec: ButtonSpec)

/** 插件手里那份页面注册器：只能 register/remove 自己的页面。 */
class PageRegistry internal constructor(
    private val core: PluginCore,
    private val owner: String,
    /** 注册被拒时的回调：插件常不检查返回值，宿主替它记下来，出问题才查得到原因。 */
    private val onReject: ((String) -> Unit)? = null,
) {
    fun add(spec: PageSpec): RegisterResult = track(core.addPage(owner, spec), "页面 " + spec.id)
    fun add(spec: ListPageSpec): RegisterResult = track(core.addPage(owner, spec), "页面 " + spec.id)

    private fun track(result: RegisterResult, what: String): RegisterResult {
        if (result is RegisterResult.Rejected) onReject?.invoke(what + "：" + result.reason)
        return result
    }

    fun remove(pageId: String): Boolean = core.removePage(owner, pageId)
    fun removeAll(): Int = core.removePagesOf(owner)
    fun all(): List<PluginPageSpec> = core.pages().filter { it.ownerPluginId == owner }.map { it.spec }
}

/** 插件手里那份按钮注册器：只能往已有的三个插槽里塞。 */
class ButtonRegistry internal constructor(
    private val core: PluginCore,
    private val owner: String,
    private val onReject: ((String) -> Unit)? = null,
) {
    fun add(spec: ButtonSpec): RegisterResult = track(core.addButton(owner, spec), "按钮 " + spec.id)

    private fun track(result: RegisterResult, what: String): RegisterResult {
        if (result is RegisterResult.Rejected) onReject?.invoke(what + "：" + result.reason)
        return result
    }

    fun remove(buttonId: String): Boolean = core.removeButton(owner, buttonId)
    fun removeAll(): Int = core.removeButtonsOf(owner)
}

/** 插件私有 kv，按 pluginId 隔离，卸载时整个 map 丢掉。 */
class KeyValueStoreImpl(private val map: MutableMap<String, Any>) : KeyValueStore {
    override fun getString(key: String): String? = map[key] as? String
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getInt(key: String, default: Int): Int = (map[key] as? Int) ?: default
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun keys(): Set<String> = map.keys.toSet()
    override fun clear() { map.clear() }
}

/** 数据接口的默认实现：权限不够就抛异常，让插件在开发期就被打脸，而不是静默拿空数据。 */
class DataApiImpl(
    private val store: Store,
    private val manifest: PluginManifest,
    private val kv: KeyValueStore,
) : DataApi {
    override fun notes(): List<Note> {
        check(manifest.permissions.contains(Permission.READ_NOTES)) {
            "插件 " + manifest.id + " 没有申请 READ_NOTES 权限"
        }
        return store.notes.toList()
    }

    override fun tags(): List<String> {
        check(
            manifest.permissions.contains(Permission.READ_NOTES) ||
                manifest.permissions.contains(Permission.READ_SETTINGS)
        ) { "插件 " + manifest.id + " 没有申请读数据的权限" }
        return store.notes.map { it.tag }.distinct().sorted()
    }

    override fun kv(): KeyValueStore = kv
}

/** 事件总线实现：插件抛的异常一律吞掉，只记一次失败给熔断用，不连累别的插件和宿主。 */
class EventBusImpl : EventBus {
    /** 宿主挂的失败回调：插件 id + 出错位置。 */
    var failureHandler: ((String, String) -> Unit)? = null

    private val noteSavedListeners = ArrayList<Pair<String?, (Note) -> Unit>>()
    private val pageShownListeners = ArrayList<Pair<String?, (String) -> Unit>>()

    override fun onNoteSaved(listener: (Note) -> Unit): () -> Unit = addNoteSaved(null, listener)
    override fun onPageShown(listener: (String) -> Unit): () -> Unit = addPageShown(null, listener)

    /** 插件注册的监听器带 owner：出错时才知道该记在哪个插件头上。 */
    fun addNoteSaved(owner: String?, listener: (Note) -> Unit): () -> Unit {
        val entry = owner to listener
        noteSavedListeners.add(entry)
        return { noteSavedListeners.remove(entry) }
    }

    fun addPageShown(owner: String?, listener: (String) -> Unit): () -> Unit {
        val entry = owner to listener
        pageShownListeners.add(entry)
        return { pageShownListeners.remove(entry) }
    }

    fun emitNoteSaved(note: Note) {
        noteSavedListeners.toList().forEach { (owner, listener) ->
            val error = runCatching { listener(note) }.exceptionOrNull()
            if (error != null && owner != null) failureHandler?.invoke(owner, "事件")
        }
    }

    fun emitPageShown(pageId: String) {
        pageShownListeners.toList().forEach { (owner, listener) ->
            val error = runCatching { listener(pageId) }.exceptionOrNull()
            if (error != null && owner != null) failureHandler?.invoke(owner, "事件")
        }
    }
}

/** 交给插件的事件订阅：注册时自动带上自己的 id，出错后熔断才能算到它头上。 */
private class OwnedEventBus(private val bus: EventBusImpl, private val owner: String) : EventBus {
    override fun onNoteSaved(listener: (Note) -> Unit): () -> Unit = bus.addNoteSaved(owner, listener)
    override fun onPageShown(listener: (String) -> Unit): () -> Unit = bus.addPageShown(owner, listener)
}

/** 交给单个插件的 host，出口全部收敛到这里。 */
class PluginHostImpl(
    override val manifest: PluginManifest,
    private val core: PluginCore,
    private val kv: KeyValueStore,
) : PluginHost {
    var plugin: WristPlugin? = null

    /** install() 期间被拒的页面/按钮注册；安装链路会写进日志，免得「装上了却什么都没出来」查不到原因。 */
    val registerRejections = ArrayList<String>()

    override val pages: PageRegistry = PageRegistry(core, manifest.id) { registerRejections.add(it) }
    override val buttons: ButtonRegistry = ButtonRegistry(core, manifest.id) { registerRejections.add(it) }
    override val data: DataApi = DataApiImpl(core.store, manifest, kv)
    override val events: EventBus = core.busOf(manifest.id)

    override fun invalidate() { core.invalidateHandler?.invoke() }
    override fun toast(message: String) { core.toastHandler?.invoke(message) }
    override fun openPage(pageId: String) { core.openPage(manifest.id, pageId) }
    override fun openHostPage(screen: HostScreen) { core.openHostPage(screen) }
}

/**
 * 插件系统的宿主核心。整个应用只存一份（在 AppRoot 里 remember 出来）。
 *
 * 三条硬规则都在这里落实：
 * 1. 能加能收：卸载按 pluginId 清页面/按钮/订阅/kv。
 * 2. 崩溃隔离：install / 事件 / 渲染全部 runCatching，插件出错不闪退宿主。
 * 3. 有上限：每插件页面数、每插槽按钮数都有硬上限。
 */
/**
 * @param kvFactory 给插件私有数据提供一个落盘实现（传了 Context 就落盘，没传就纯内存，自测用）。
 */
class PluginCore(val store: Store, private val kvFactory: ((String) -> KeyValueStore)? = null) {
    private val pages = mutableStateListOf<PluginPage>()
    private val buttons = mutableStateListOf<PluginButton>()
    val manifests = mutableStateListOf<PluginManifest>()

    var toastHandler: ((String) -> Unit)? = null
    var invalidateHandler: (() -> Unit)? = null

    /** 宿主导航：插件想开自己的页面时，由宿主决定往栈里压一页。 */
    var navigateHandler: ((String) -> Unit)? = null

    /** 官方页面导航：插件引用 HostScreen 时，由宿主决定压哪一页（白名单在 enum 里）。 */
    var hostNavigateHandler: ((HostScreen) -> Unit)? = null

    private val hosts = HashMap<String, PluginHostImpl>()
    private val kvStores = HashMap<String, KeyValueStore>()
    private val bus = EventBusImpl()

    /** 最近一次安装失败的原因，管理页要把它显示出来（下次装成功才清）。 */
    private val installErrors = HashMap<String, String>()

    /** 每个插件连续出错了几次；成功一次或重新装上就清零。 */
    private val failureCounts = HashMap<String, Int>()

    init {
        bus.failureHandler = { pluginId, where -> recordFailure(pluginId, where) }
    }

    private fun kvOf(pluginId: String): KeyValueStore =
        kvStores.getOrPut(pluginId) { kvFactory?.invoke(pluginId) ?: KeyValueStoreImpl(HashMap<String, Any>()) }

    fun events(): EventBus = bus

    /** 给某个插件开一个带 owner 的事件订阅口。 */
    internal fun busOf(pluginId: String): EventBus = OwnedEventBus(bus, pluginId)
    fun notifyNoteSaved(note: Note) { bus.emitNoteSaved(note) }
    fun notifyPageShown(pageId: String) { bus.emitPageShown(pageId) }

    fun pages(): List<PluginPage> = pages.toList()
    fun buttons(): List<PluginButton> = buttons.toList()
    fun page(pageId: String): PluginPageSpec? = pages.firstOrNull { it.spec.id == pageId }?.spec
    fun isInstalled(pluginId: String): Boolean = hosts.containsKey(pluginId)

    /** 某个插件注册了哪些页面 / 按钮（管理页详情用，只读）。 */
    fun pagesOf(pluginId: String): List<PluginPage> = pages.filter { it.ownerPluginId == pluginId }
    fun buttonsOfPlugin(pluginId: String): List<PluginButton> = buttons.filter { it.ownerPluginId == pluginId }

    /** 某个插件私有数据现在存了哪些键（只给键名，不显示值）。 */
    fun dataKeysOf(pluginId: String): Set<String> = kvStores[pluginId]?.keys() ?: emptySet()

    /** 最近一次安装失败原因；没失败过就是 null。 */
    fun lastInstallError(pluginId: String): String? = installErrors[pluginId]

    /** 某个插件当前连续出错次数（管理页详情用）。 */
    fun failuresOf(pluginId: String): Int = failureCounts[pluginId] ?: 0

    /**
     * 记一次插件运行时错误。连续 [MAX_PLUGIN_FAILURES] 次就把这个插件自动停用：
     * 页面/按钮收走、私有数据保留、原因记进 installErrors 给管理页看，用户可以再手动开回来。
     * 返回 true 表示这一次触发了自动停用。
     */
    fun recordFailure(pluginId: String, where: String): Boolean {
        val count = (failureCounts[pluginId] ?: 0) + 1
        if (count < MAX_PLUGIN_FAILURES) {
            failureCounts[pluginId] = count
            return false
        }
        failureCounts.remove(pluginId)
        val name = manifests.firstOrNull { it.id == pluginId }?.name ?: pluginId
        installErrors[pluginId] = "连续出错 " + MAX_PLUGIN_FAILURES + " 次（" + where + "），已自动停用"
        disable(pluginId)
        toastHandler?.invoke("插件「" + name + "」连续出错 " + MAX_PLUGIN_FAILURES + " 次，已自动停用")
        return true
    }

    /** 某个插槽里现在有哪些按钮（宿主 UI 直接读这个画）。 */
    fun buttonsOf(slot: ButtonSlot): List<PluginButton> = buttons.filter { it.spec.slot == slot }

    /** 只要某个插件塞进某个插槽的按钮 —— 插件页面底部的按钮只认自己的主人。 */
    fun buttonsOf(ownerPluginId: String, slot: ButtonSlot): List<PluginButton> =
        buttons.filter { it.ownerPluginId == ownerPluginId && it.spec.slot == slot }

    /** 这个页面归哪个插件，用来决定它底部该显示哪些按钮。 */
    fun ownerOfPage(pageId: String): String? = pages.firstOrNull { it.spec.id == pageId }?.ownerPluginId

    /** 执行插件按钮：插件里抛错只弹一句提示，不让宿主闪退。 */
    fun runButton(button: PluginButton) {
        try {
            button.spec.onClick()
            failureCounts.remove(button.ownerPluginId)
        } catch (t: Throwable) {
            toastHandler?.invoke("插件按钮出错：" + (t.message ?: t.javaClass.simpleName))
            recordFailure(button.ownerPluginId, "按钮")
        }
    }

    /**
     * 执行卡片上的插件动作：把被点的那条笔记交给插件。
     * 同样做崩溃隔离——插件在卡片按钮里抛错，只弹一句提示，笔记列表不闪退。
     */
    fun runCardAction(button: PluginButton, note: Note) {
        try {
            button.spec.onCardAction?.invoke(note)
            failureCounts.remove(button.ownerPluginId)
        } catch (t: Throwable) {
            toastHandler?.invoke("插件卡片按钮出错：" + (t.message ?: t.javaClass.simpleName))
            recordFailure(button.ownerPluginId, "卡片按钮")
        }
    }

    /**
     * 插件打开自己的页面：先验页面存在、再验 owner，最后才交给宿主导航。
     * 这样插件既没法打开别人的页面，也没法指向一个不存在的页面留下死胡同。
     */
    fun openPage(ownerPluginId: String, pageId: String): Boolean {
        if (page(pageId) == null) {
            toastHandler?.invoke("插件页面不存在：" + pageId)
            return false
        }
        if (ownerOfPage(pageId) != ownerPluginId) {
            toastHandler?.invoke("插件不能打开别的插件的页面")
            return false
        }
        val nav = navigateHandler
        if (nav == null) {
            toastHandler?.invoke("宿主导航未就绪")
            return false
        }
        nav(pageId)
        return true
    }

    /**
     * 插件引用官方页面：能开什么由 HostScreen 白名单决定，这里只负责交给宿主压栈。
     * 宿主没接导航时返回 false 并提示，不抛异常（插件不应该因为宿主没接好就崩）。
     */
    fun openHostPage(screen: HostScreen): Boolean {
        val nav = hostNavigateHandler
        if (nav == null) {
            toastHandler?.invoke("宿主导航未就绪")
            return false
        }
        nav(screen)
        return true
    }

    /** 装上插件。安装失败会把已注册的东西全部回滚，宿主状态不变，原因记下来给管理页看。 */
    fun install(plugin: WristPlugin): RegisterResult {
        val manifest = plugin.manifest
        val blocked = when {
            manifest.minApi > PluginApi.API_LEVEL ->
                "需要宿主 API " + manifest.minApi + "，当前只有 " + PluginApi.API_LEVEL
            hosts.containsKey(manifest.id) -> "插件已安装：" + manifest.id
            else -> null
        }
        if (blocked != null) {
            installErrors[manifest.id] = blocked
            return RegisterResult.Rejected(blocked)
        }
        val host = PluginHostImpl(manifest, this, kvOf(manifest.id))
        host.plugin = plugin
        hosts[manifest.id] = host
        return try {
            plugin.install(host)
            if (host.registerRejections.isNotEmpty()) {
                EventLog.add("plugin", "插件 " + manifest.id + " 安装时有注册被拒绝：" + host.registerRejections.joinToString("；"))
            }
            manifests.add(manifest)
            installErrors.remove(manifest.id)
            failureCounts.remove(manifest.id)
            RegisterResult.Ok
        } catch (t: Throwable) {
            val reason = "安装时抛错：" + (t.message ?: t.javaClass.simpleName)
            installErrors[manifest.id] = reason
            uninstall(manifest.id)
            RegisterResult.Rejected(reason)
        }
    }

    /**
     * 重新加载一个正在运行的插件：先按「关掉」收走页面/按钮，但数据留着，
     * 再重新 install 一遍。插件自己状态乱了时用这个，比卸载重装安全（不会清数据）。
     */
    fun reload(plugin: WristPlugin): RegisterResult {
        val id = plugin.manifest.id
        if (!hosts.containsKey(id)) return RegisterResult.Rejected("插件未安装：" + id)
        disable(id)
        return install(plugin)
    }

    /** 卸载插件：先让它自己清，再按 pluginId 兜底清一遍。 */
    fun uninstall(pluginId: String): Boolean {
        val host = hosts.remove(pluginId) ?: return false
        runCatching { host.plugin?.uninstall() }
        removePagesOf(pluginId)
        removeButtonsOf(pluginId)
        manifests.removeAll { it.id == pluginId }
        kvStores.remove(pluginId)?.clear()
        failureCounts.remove(pluginId)
        return true
    }

    /**
     * 用户在「插件管理页」把插件关掉：页面、按钮、清单都收走，但**保留插件自己的数据**，
     * 下次打开还是原来的样子。只有真卸载（uninstall）才清 kv。
     */
    fun disable(pluginId: String): Boolean {
        val host = hosts.remove(pluginId) ?: return false
        runCatching { host.plugin?.uninstall() }
        removePagesOf(pluginId)
        removeButtonsOf(pluginId)
        manifests.removeAll { it.id == pluginId }
        failureCounts.remove(pluginId)
        return true
    }

    internal fun addPage(owner: String, spec: PageSpec): RegisterResult = addPageChecked(owner, spec)
    internal fun addPage(owner: String, spec: ListPageSpec): RegisterResult = addPageChecked(owner, spec)

    /** 内容页和列表页共用的校验：命名空间、重复、每插件页数上限。 */
    private fun addPageChecked(owner: String, spec: PluginPageSpec): RegisterResult {
        if (!hosts.containsKey(owner)) return RegisterResult.Rejected("插件未安装：" + owner)
        val prefix = owner + ":"
        if (!spec.id.startsWith(prefix)) {
            return RegisterResult.Rejected("页面 id 必须用插件 id 加冒号开头：" + prefix + "xxx")
        }
        if (pages.any { it.spec.id == spec.id }) {
            return RegisterResult.Rejected("页面 id 已被占用：" + spec.id)
        }
        if (pages.count { it.ownerPluginId == owner } >= MAX_PAGES_PER_PLUGIN) {
            return RegisterResult.Rejected("每个插件最多 " + MAX_PAGES_PER_PLUGIN + " 页")
        }
        pages.add(PluginPage(owner, spec))
        return RegisterResult.Ok
    }

    internal fun removePage(owner: String, pageId: String): Boolean {
        val target = pages.firstOrNull { it.ownerPluginId == owner && it.spec.id == pageId } ?: return false
        pages.remove(target)
        return true
    }

    internal fun removePagesOf(owner: String): Int {
        val target = pages.filter { it.ownerPluginId == owner }
        pages.removeAll(target)
        return target.size
    }

    internal fun addButton(owner: String, spec: ButtonSpec): RegisterResult {
        if (!hosts.containsKey(owner)) return RegisterResult.Rejected("插件未安装：" + owner)
        if (buttons.any { it.spec.id == spec.id }) {
            return RegisterResult.Rejected("按钮 id 已被占用：" + spec.id)
        }
        if (spec.slot == ButtonSlot.CARD_ACTION && spec.onCardAction == null) {
            return RegisterResult.Rejected("CARD_ACTION 按钮必须提供 onCardAction（否则不知道点的是哪条笔记）")
        }
        if (spec.slot != ButtonSlot.CARD_ACTION && spec.onCardAction != null) {
            return RegisterResult.Rejected("只有 CARD_ACTION 按钮能带 onCardAction")
        }
        if (buttons.count { it.spec.slot == spec.slot } >= MAX_BUTTONS_PER_SLOT) {
            return RegisterResult.Rejected("插槽 " + spec.slot + " 已满（最多 " + MAX_BUTTONS_PER_SLOT + " 个）")
        }
        buttons.add(PluginButton(owner, spec))
        return RegisterResult.Ok
    }

    internal fun removeButton(owner: String, buttonId: String): Boolean {
        val target = buttons.firstOrNull { it.ownerPluginId == owner && it.spec.id == buttonId } ?: return false
        buttons.remove(target)
        return true
    }

    internal fun removeButtonsOf(owner: String): Int {
        val target = buttons.filter { it.ownerPluginId == owner }
        buttons.removeAll(target)
        return target.size
    }
}
