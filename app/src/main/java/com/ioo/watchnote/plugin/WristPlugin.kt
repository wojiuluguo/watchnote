package com.ioo.watchnote.plugin

/**
 * 插件本体。宿主只认这个接口，不认具体类。
 *
 * 约定：
 * 1. install() 里只能调 host 给的东西（注册页面/按钮、读数据、订阅事件），
 *    不许直接碰宿主导航栈、不许拿 Activity。
 * 2. install() 抛异常 = 安装失败，宿主会把这个插件已经注册的东西全部回滚。
 * 3. uninstall() 由宿主在关闭插件时调用；就算插件忘了清，宿主也会按 pluginId 清干净。
 */
interface WristPlugin {
    val manifest: PluginManifest
    fun install(host: PluginHost)
    fun uninstall()
}

/**
 * 宿主交给插件的能力集合。一个插件拿到一个 host，作用域只在这个插件自己身上。
 */
interface PluginHost {
    val manifest: PluginManifest
    val pages: PageRegistry
    val buttons: ButtonRegistry
    val data: DataApi
    val events: EventBus

    /**
     * 前置模块查询口（0.3 起）：装了什么前置模块、它提供了哪些符号、哪些和宿主同名被关掉了。
     * 插件用 [LibraryApi.available] 决定要不要降级，而不是把整个插件判死。
     */
    val libraries: LibraryApi

    /** 宿主界面状态被插件改了（比如换了主题色），喊一声让宿主重画。 */
    fun invalidate()

    /** 给用户看一句短提示。 */
    fun toast(message: String)

    /**
     * 打开本插件自己的一个页面（由宿主压栈）。
     * 只能开注册过、且 owner 是自己的页面；开别人的会被拒绝并弹提示。
     */
    fun openPage(pageId: String)

    /**
     * 打开宿主官方页面（HostScreen 白名单里的那一批，4.1.8 起）。
     * 页面由宿主自己渲染，插件拿不到导航栈也改不了页面内容；
     * 宿主导航还没接上时只弹一句提示，不会抛异常。
     */
    fun openHostPage(screen: HostScreen)
}
