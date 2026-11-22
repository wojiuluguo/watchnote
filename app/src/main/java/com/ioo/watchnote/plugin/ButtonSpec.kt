package com.ioo.watchnote.plugin

import com.ioo.watchnote.Note

/**
 * 按钮插槽。v1 三个都是宿主已经存在的 UI 位置，插件只能往里塞，
 * 不能自己新造一排按钮 —— 这是「不把圆屏塞满」的底线。
 */
enum class ButtonSlot {
    PAGE_BOTTOM,   // 列表页底部那颗圆按钮
    CARD_ACTION,   // 卡片右侧的动作
    SETTINGS_ROW,  // 设置页的一行
}

/**
 * 一个按钮的声明。
 *
 * CARD_ACTION 和其它两个插槽不一样：卡片上的按钮必须知道**用户点的是哪条笔记**，
 * 所以它必须提供 onCardAction（收到那条 Note）；如果只给 onClick，宿主会拒绝注册，
 * 免得插件在卡片上放一个不知道对谁生效的按钮。
 */
data class ButtonSpec(
    val id: String,
    val slot: ButtonSlot,
    val icon: String,
    val label: String,
    /** PAGE_BOTTOM / SETTINGS_ROW 点一下做什么；CARD_ACTION 不用这个（用 onCardAction）。 */
    val onClick: () -> Unit,
    /** 只有 CARD_ACTION 用：点的是哪条笔记，就把那条笔记交给插件。 */
    val onCardAction: ((Note) -> Unit)? = null,
)
