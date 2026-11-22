package com.ioo.watchnote.plugin

import com.ioo.watchnote.Note

/** 插件自己的小存储：按 pluginId 隔离，卸载时一起清掉。 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun remove(key: String)

    /** 这个插件现在存了哪些键（管理页展示用，值不外传）。 */
    fun keys(): Set<String>

    /** 清空这个插件的全部私有数据。真卸载时宿主会调用；管理页里「关掉」不会。 */
    fun clear()
}

/**
 * 插件能拿到的数据。v1 全是只读快照 + 自己的 kv，
 * 写笔记/写设置要等权限模型稳定了再开（见 READ_NOTES / WRITE_NOTES）。
 */
interface DataApi {
    fun notes(): List<Note>
    fun tags(): List<String>
    fun kv(): KeyValueStore
}

/**
 * 事件总线。返回的是「取消订阅」函数，插件卸载前自己调一下；
 * 忘了调也没事，宿主卸载时会把这个插件的订阅全删掉。
 */
interface EventBus {
    fun onNoteSaved(listener: (Note) -> Unit): () -> Unit
    fun onPageShown(listener: (String) -> Unit): () -> Unit
}
