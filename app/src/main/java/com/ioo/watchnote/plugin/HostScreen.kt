package com.ioo.watchnote.plugin

/**
 * 插件可以「直接引用」的官方页面（4.1.8 起）。
 *
 * 插件不该重画宿主的页面——重画的迟早和宿主对不上（多一个设置项、改了字号、圆屏边距不一样）。
 * 所以这里给一个白名单：插件只说「我要去哪个官方页」，由宿主压栈打开，
 * 页面还是宿主自己那一份，返回键、圆屏安全区、动画全部自动一致。
 *
 * 注意这是导航白名单，不是权限：能打开的只有下面这些只读入口，
 * 编辑页、设置页正文、备份恢复这类会改数据的地方不在列表里，插件碰不到。
 * 新给插件开放一个官方页面时：只改这个 enum + AppRoot 里的一行 when，插件侧一行都不用动。
 */
enum class HostScreen(val title: String) {
    ABOUT("关于"),
    SELF_TEST("自测工具"),
    OPEN_SOURCE("开源网址"),
    PLUGIN_MANAGER("插件"),
    MANAGE("时间表管理"),
    SPECIAL_LIST("特殊时段"),
    SHARE_TO_PHONE("发到手机"),
    ANIM_MODE("动画效果"),
}
