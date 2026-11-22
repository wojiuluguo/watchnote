package com.ioo.watchnote

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** 全局 UI 状态：顶部时间开关（Compose state，任何页面直接读取，避免参数传递导致不同步）。 */
object UiPrefsState {
    val showTopTime = mutableStateOf(false)
    val accent = mutableStateOf(0)
    val animMode = mutableStateOf(0)
    val haptics = mutableStateOf(true)

    /** 自定义强调色（ARGB；0 = 用预设色板）。customAccentName 显示在主题页 / 更多颜色里。 */
    val customAccent = mutableStateOf(0)
    var customAccentName by mutableStateOf("")
}

/** SnapshotStateList 上用 filter+clear+addAll 做删除，避免迭代器删除的兼容问题。 */
internal fun <T> androidx.compose.runtime.snapshots.SnapshotStateList<T>.removeMatching(predicate: (T) -> Boolean) {
    val keep = this.filterNot(predicate).toList()
    this.clear()
    this.addAll(keep)
}

class Note(
    id: String = "",
    text: String = "",
    tag: String = "随手记",
    marked: Boolean = false,
    done: Boolean = false,
    todo: Boolean = false,
    dueAt: Long = 0,
    endAt: Long = 0,
    audioPath: String = "",
    audioDur: Long = 0,
    createdAt: Long = 0,
    updatedAt: Long = 0
) {
    var id by mutableStateOf(id)
    var text by mutableStateOf(text)
    var tag by mutableStateOf(tag)
    var marked by mutableStateOf(marked)
    var done by mutableStateOf(done)
    var todo by mutableStateOf(todo)
    var dueAt by mutableStateOf(dueAt)
    var endAt by mutableStateOf(endAt)
    var audioPath by mutableStateOf(audioPath)
    var audioDur by mutableStateOf(audioDur)
    var createdAt by mutableStateOf(createdAt)
    var updatedAt by mutableStateOf(updatedAt)
}

class Slot(
    id: String = "",
    name: String = "",
    type: String = "class",
    start: String = "08:00",
    end: String = "08:45",
    days: List<Int> = emptyList()
) {
    var id by mutableStateOf(id)
    var name by mutableStateOf(name)
    var type by mutableStateOf(type)
    var start by mutableStateOf(start)
    var end by mutableStateOf(end)
    val days = mutableStateListOf<Int>().also { it.addAll(days) }
}

data class GroupItem(
    val start: String = "08:00",
    val end: String = "08:45",
    val label: String = ""
)

class TimeGroup(
    id: String = "",
    name: String = "",
    items: List<GroupItem> = emptyList()
) {
    var id by mutableStateOf(id)
    var name by mutableStateOf(name)
    val items = mutableStateListOf<GroupItem>().also { it.addAll(items) }
}

class SpecialRule(
    id: String = "",
    weekday: Int = 0,
    startEpochDay: Long = 0,
    weeks: Int = 1,
    closed: Boolean = false,
    items: List<GroupItem> = emptyList()
) {
    var id by mutableStateOf(id)
    var weekday by mutableStateOf(weekday)
    var startEpochDay by mutableStateOf(startEpochDay)
    var weeks by mutableStateOf(weeks)
    var closed by mutableStateOf(closed)
    val items = mutableStateListOf<GroupItem>().also { it.addAll(items) }

    fun endEpochDay(): Long = startEpochDay + 7L * (weeks - 1).coerceAtLeast(0)
    fun dateFor(k: Int): Long = startEpochDay + 7L * k
}

class StateInfo(
    val active: Slot?,
    val activeStart: Int,
    val activeEnd: Int,
    val next: Slot?,
    val nextStart: Int,
    val nextOffset: Int,
    val nextIn: Double,
    val remain: Double,
    val progress: Float
)

class Store(ctx: Context) {
    /** Compose 数据版本号：每次保存 +1，页面读取它即可立即刷新（解决改完要等很久才同步）。 */
    val version = androidx.compose.runtime.mutableIntStateOf(0)
    private val prefs = ctx.getSharedPreferences("watchnote_compose", Context.MODE_PRIVATE)
    val notes = mutableStateListOf<Note>()
    val slots = mutableStateListOf<Slot>()
    val groups = mutableStateListOf<TimeGroup>()
    val specials = mutableStateListOf<SpecialRule>()

    companion object {
        val TAG_LIST = listOf("作业", "语文", "数学", "英语", "随手记")
        val DAY_NAMES = listOf("日", "一", "二", "三", "四", "五", "六")
        val TYPE_KEYS = listOf("class", "meal", "rest", "other")
        val TYPE_LABELS = listOf("上课", "吃饭", "休息", "其他")

        fun uid(): String = UUID.randomUUID().toString().replace("-", "").substring(0, 12)

        fun minsOf(hhmm: String): Int {
            val parts = hhmm.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            return (((h * 60 + m) % 1440) + 1440) % 1440
        }

        fun fmtMins(minutes: Int): String {
            val m = ((minutes % 1440) + 1440) % 1440
            return String.format(Locale.CHINA, "%02d:%02d", m / 60, m % 60)
        }

        fun typeLabel(type: String): String {
            val i = TYPE_KEYS.indexOf(type)
            return if (i >= 0) TYPE_LABELS[i] else "其他"
        }

        /* 夜墨语义色：朱砂=上课 / 琥珀=吃饭 / 绿=休息 / 灰=其他 */
        fun typeColor(type: String): androidx.compose.ui.graphics.Color = when (type) {
            "class" -> androidx.compose.ui.graphics.Color(0xFFFF6A4D)
            "meal" -> androidx.compose.ui.graphics.Color(0xFFFFC46B)
            "rest" -> androidx.compose.ui.graphics.Color(0xFF7BD88F)
            else -> androidx.compose.ui.graphics.Color(0xFFA8A29A)
        }

        fun typeBg(type: String): androidx.compose.ui.graphics.Color = when (type) {
            "class" -> androidx.compose.ui.graphics.Color(0xFF3A1C14)
            "meal" -> androidx.compose.ui.graphics.Color(0xFF382C16)
            "rest" -> androidx.compose.ui.graphics.Color(0xFF17301F)
            else -> androidx.compose.ui.graphics.Color(0xFF26262A)
        }

        fun nowMins(): Double {
            val c = Calendar.getInstance()
            return c.get(Calendar.HOUR_OF_DAY) * 60.0 + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60.0
        }

        fun todayIndex(): Int = java.time.LocalDate.now().dayOfWeek.value % 7

        fun fmtDate(ms: Long = System.currentTimeMillis()): String {
            val d = Date(ms)
            val w = listOf("日", "一", "二", "三", "四", "五", "六")
            val c = Calendar.getInstance()
            c.time = d
            return (c.get(Calendar.MONTH) + 1).toString() + "月" + c.get(Calendar.DAY_OF_MONTH) + "日 周" + w[c.get(Calendar.DAY_OF_WEEK) - 1]
        }

        fun fmtStamp(ms: Long): String = SimpleDateFormat("M/d HH:mm", Locale.CHINA).format(Date(ms))

        fun fmtDayEpoch(epochDay: Long): String = SimpleDateFormat("M/d", Locale.CHINA).format(Date(epochDay * 86_400_000L))
        fun fmtClock(ms: Long = System.currentTimeMillis()): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ms))

        /** 今天 0 点。 */
        fun startOfToday(): Long {
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        fun hhmm(ms: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ms))

        fun fmtDay(ms: Long): String = SimpleDateFormat("M/d", Locale.CHINA).format(Date(ms))

        /** 「12/4 20:00-21:00」；同日只写一次日期。 */
        fun rangeLabel(start: Long, end: Long): String {
            if (start <= 0L) return ""
            if (end <= start) return fmtDay(start) + " " + hhmm(start)
            val sameDay = Calendar.getInstance().apply { timeInMillis = start }.let { a ->
                val b = Calendar.getInstance().apply { timeInMillis = end }
                a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
            }
            return if (sameDay) fmtDay(start) + " " + hhmm(start) + "-" + hhmm(end)
            else fmtDay(start) + " " + hhmm(start) + " - " + fmtDay(end) + " " + hhmm(end)
        }

        fun durationLabel(start: Long, end: Long): String {
            val mins = (((end - start) / 60_000L).toInt()).coerceAtLeast(0)
            val h = mins / 60
            val m = mins % 60
            return when {
                h > 0 && m > 0 -> h.toString() + " 小时 " + m + " 分"
                h > 0 -> h.toString() + " 小时"
                else -> m.toString() + " 分钟"
            }
        }
    }

    fun getAccent(): Int = UiPrefsState.accent.value

    fun setAccent(value: Int) {
        UiPrefsState.accent.value = value
        UiPrefsState.customAccent.value = 0
        UiPrefsState.customAccentName = ""
        prefs.edit()
            .putInt("accent", value)
            .putInt("accent_color", 0)
            .putString("accent_name", "")
            .apply()
        EventLog.add("setting", "accent=" + value)
    }

    /** 用「更多颜色」里挑的中国传统色当强调色（argb = 0 表示清掉自定义、回到预设）。 */
    fun setAccentColor(argb: Int, name: String) {
        UiPrefsState.customAccent.value = argb
        UiPrefsState.customAccentName = name
        prefs.edit()
            .putInt("accent_color", argb)
            .putString("accent_name", name)
            .apply()
        EventLog.add("setting", "accentColor=" + Integer.toHexString(argb) + " " + name)
    }

    fun getHaptics(): Boolean = UiPrefsState.haptics.value

    fun setHaptics(value: Boolean) {
        UiPrefsState.haptics.value = value
        prefs.edit().putBoolean("haptics", value).apply()
    }

    fun getAnimMode(): Int = UiPrefsState.animMode.value

    fun setAnimMode(value: Int) {
        UiPrefsState.animMode.value = value
        prefs.edit().putInt("anim_mode", value).apply()
        EventLog.add("setting", "animMode=" + value)
    }

    fun getShowTopTime(): Boolean = UiPrefsState.showTopTime.value

    fun setShowTopTime(value: Boolean) {
        UiPrefsState.showTopTime.value = value
        prefs.edit().putBoolean("show_top_time", value).apply()
        EventLog.add("setting", "topTime=" + value)
    }

    init {
        load()
        UiPrefsState.showTopTime.value = prefs.getBoolean("show_top_time", false)
        UiPrefsState.accent.value = prefs.getInt("accent", 0)
        UiPrefsState.customAccent.value = prefs.getInt("accent_color", 0)
        UiPrefsState.customAccentName = prefs.getString("accent_name", "") ?: ""
        UiPrefsState.animMode.value = prefs.getInt("anim_mode", 0)
        UiPrefsState.haptics.value = prefs.getBoolean("haptics", true)
    }

    /* ---------------- 持久化：一处序列化，保存 / 导出 / 导入共用 ---------------- */

    private fun noteJson(n: Note): JSONObject = JSONObject().also { o ->
        o.put("id", n.id)
        o.put("text", n.text)
        o.put("tag", n.tag)
        o.put("marked", n.marked)
        o.put("done", n.done)
        o.put("todo", n.todo)
        o.put("dueAt", n.dueAt)
        o.put("endAt", n.endAt)
        o.put("audioPath", n.audioPath)
        o.put("audioDur", n.audioDur)
        o.put("createdAt", n.createdAt)
        o.put("updatedAt", n.updatedAt)
    }

    private fun slotJson(s: Slot): JSONObject = JSONObject().also { o ->
        o.put("id", s.id); o.put("name", s.name); o.put("type", s.type)
        o.put("start", s.start); o.put("end", s.end)
        val da = JSONArray(); s.days.forEach { da.put(it) }
        o.put("days", da)
    }

    private fun itemsJson(items: List<GroupItem>): JSONArray = JSONArray().also { ia ->
        items.forEach { item ->
            ia.put(JSONObject().also { io ->
                io.put("start", item.start)
                io.put("end", item.end)
                io.put("label", item.label)
            })
        }
    }

    private fun groupJson(g: TimeGroup): JSONObject = JSONObject().also { o ->
        o.put("id", g.id)
        o.put("name", g.name)
        o.put("items", itemsJson(g.items))
    }

    private fun specialJson(r: SpecialRule): JSONObject = JSONObject().also { o ->
        o.put("id", r.id)
        o.put("weekday", r.weekday)
        o.put("start", r.startEpochDay)
        o.put("weeks", r.weeks)
        o.put("closed", r.closed)
        o.put("items", itemsJson(r.items))
    }

    /** 当前全部数据（保存与导出一致，导入能原样还原）。 */
    private fun snapshot(): JSONObject = JSONObject().also { root ->
        root.put("schema", 2)
        root.put("notes", JSONArray().also { a -> notes.forEach { a.put(noteJson(it)) } })
        root.put("slots", JSONArray().also { a -> slots.forEach { a.put(slotJson(it)) } })
        root.put("groups", JSONArray().also { a -> groups.forEach { a.put(groupJson(it)) } })
        root.put("specials", JSONArray().also { a -> specials.forEach { a.put(specialJson(it)) } })
    }

    private fun noteFrom(o: JSONObject): Note = Note(
        id = o.optString("id", "").ifBlank { uid() },
        text = o.optString("text", ""),
        tag = o.optString("tag", "随手记"),
        marked = o.optBoolean("marked", false),
        done = o.optBoolean("done", false),
        todo = o.optBoolean("todo", o.optLong("dueAt", 0L) > 0L),
        dueAt = o.optLong("dueAt", 0L),
        endAt = o.optLong("endAt", 0L),
        audioPath = o.optString("audioPath", ""),
        audioDur = o.optLong("audioDur", 0L),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun slotFrom(o: JSONObject): Slot {
        val s = Slot(
            id = o.optString("id", "").ifBlank { uid() },
            name = o.optString("name", "未命名"),
            type = o.optString("type", "class"),
            start = o.optString("start", "08:00"),
            end = o.optString("end", "08:45")
        )
        val da = o.optJSONArray("days")
        if (da != null) for (j in 0 until da.length()) {
            val d = da.optInt(j, -1)
            if (d in 0..6 && !s.days.contains(d)) s.days.add(d)
        }
        return s
    }

    private fun itemsFrom(o: JSONObject): List<GroupItem> {
        val out = ArrayList<GroupItem>()
        val ia = o.optJSONArray("items") ?: return out
        for (j in 0 until ia.length()) {
            val io = ia.optJSONObject(j) ?: continue
            out.add(GroupItem(io.optString("start", "08:00"), io.optString("end", "08:45"), io.optString("label", "")))
        }
        return out
    }

    /** 把 JSON 解析进临时列表；失败就抛异常，调用方保留旧数据。 */
    private fun collect(
        root: JSONObject,
        outNotes: MutableList<Note>,
        outSlots: MutableList<Slot>,
        outGroups: MutableList<TimeGroup>,
        outSpecials: MutableList<SpecialRule>
    ) {
        root.optJSONArray("notes")?.let { na ->
            for (i in 0 until na.length()) {
                val o = na.optJSONObject(i) ?: continue
                outNotes.add(noteFrom(o))
            }
        }
        root.optJSONArray("slots")?.let { sa ->
            for (i in 0 until sa.length()) {
                val o = sa.optJSONObject(i) ?: continue
                outSlots.add(slotFrom(o))
            }
        }
        root.optJSONArray("groups")?.let { ga ->
            for (i in 0 until ga.length()) {
                val o = ga.optJSONObject(i) ?: continue
                outGroups.add(
                    TimeGroup(
                        id = o.optString("id", "").ifBlank { uid() },
                        name = o.optString("name", "时间组"),
                        items = itemsFrom(o)
                    )
                )
            }
        }
        root.optJSONArray("specials")?.let { spa ->
            for (i in 0 until spa.length()) {
                val o = spa.optJSONObject(i) ?: continue
                outSpecials.add(
                    SpecialRule(
                        id = o.optString("id", "").ifBlank { uid() },
                        weekday = o.optInt("weekday", 0),
                        startEpochDay = o.optLong("start", 0L),
                        weeks = o.optInt("weeks", 1),
                        closed = o.optBoolean("closed", false),
                        items = itemsFrom(o)
                    )
                )
            }
        }
        // 空 id / 重复 id 一律换新，避免 LazyList key 冲突和「点第 2 条打开第 1 条」
        val seen = HashSet<String>()
        outNotes.forEach { if (!seen.add(it.id)) it.id = uid() }
        outSlots.forEach { if (!seen.add(it.id)) it.id = uid() }
        outGroups.forEach { if (!seen.add(it.id)) it.id = uid() }
        outSpecials.forEach { if (!seen.add(it.id)) it.id = uid() }
    }

    private fun applyIn(
        newNotes: List<Note>,
        newSlots: List<Slot>,
        newGroups: List<TimeGroup>,
        newSpecials: List<SpecialRule>
    ) {
        notes.clear(); notes.addAll(newNotes)
        slots.clear(); slots.addAll(newSlots)
        groups.clear(); groups.addAll(newGroups)
        specials.clear(); specials.addAll(newSpecials)
    }

    /**
     * 读盘：先解析到临时列表，成功才替换。
     * 坏 JSON 只备份原文（data_json_broken_<时间>）并保留当前数据，绝不清库。
     */
    private fun load() {
        val raw = prefs.getString("data_json", null)
        if (raw == null) {
            resetSchedule(saveNow = false)
            save()
            return
        }
        val n = ArrayList<Note>()
        val s = ArrayList<Slot>()
        val g = ArrayList<TimeGroup>()
        val sp = ArrayList<SpecialRule>()
        try {
            collect(JSONObject(raw), n, s, g, sp)
        } catch (e: Exception) {
            prefs.edit().putString("data_json_broken_" + System.currentTimeMillis(), raw).apply()
            EventLog.add("store", "load fail: " + e)
            return
        }
        if (s.isEmpty()) {
            resetSchedule(saveNow = false)
            s.addAll(slots)
        }
        applyIn(n, s, g, sp)
        save()
    }

    fun save() {
        try {
            val json = snapshot().toString()
            if (json == prefs.getString("data_json", null)) return
            prefs.edit().putString("data_json", json).apply()
            version.intValue = version.intValue + 1
        } catch (e: Exception) {
            EventLog.add("store", "save fail: " + e)
        }
    }

    fun notesSorted(): List<Note> = notes.sortedWith(compareBy<Note> { it.done }.thenByDescending { it.updatedAt })

    fun getNote(id: String): Note? = notes.firstOrNull { it.id == id }

    fun saveNote(draft: Note): Note? {
        val now = System.currentTimeMillis()
        val target: Note
        if (draft.id.isNotEmpty()) {
            target = getNote(draft.id) ?: return null
        } else {
            val hasContent = draft.text.trim().isNotEmpty() || draft.audioPath.isNotEmpty() || draft.todo
            if (!hasContent) return null
            draft.id = uid()
            if (draft.createdAt <= 0L) draft.createdAt = now
            notes.add(draft)
            target = draft
        }
        target.text = draft.text
        target.tag = draft.tag
        target.marked = draft.marked
        target.done = draft.done
        target.todo = draft.todo
        target.dueAt = draft.dueAt
        target.endAt = draft.endAt
        target.audioPath = draft.audioPath
        target.audioDur = draft.audioDur
        target.updatedAt = now
        save()
        return target
    }

    fun deleteNote(id: String) {
        val n = getNote(id)
        if (n != null && n.audioPath.isNotEmpty()) {
            try { java.io.File(n.audioPath).delete() } catch (e: Exception) { }
        }
        notes.removeMatching { it.id == id }
        save()
    }

    fun toggleMark(id: String) {
        getNote(id)?.let { it.marked = !it.marked; save() }
    }

    fun toggleDone(id: String) {
        getNote(id)?.let { it.done = !it.done; save() }
    }

    /** 办事：没办完的在前，再按开始时间，没时间的排最后。 */
    fun todoNotes(): List<Note> = notes.filter { it.todo }.sortedWith(
        compareBy<Note> { it.done }.thenBy { if (it.dueAt > 0) it.dueAt else Long.MAX_VALUE }.thenByDescending { it.createdAt }
    )

    fun countTodo(): Int = notes.count { it.todo && !it.done }

    /** 今天的下一件事（给首页/角标用）：没办完、有开始时间、结束时间还没过。 */
    fun nextTodo(nowMs: Long = System.currentTimeMillis()): Note? = notes
        .filter { it.todo && !it.done && it.dueAt > nowMs }
        .minByOrNull { it.dueAt }

    fun groupsSorted(): List<TimeGroup> = groups.sortedBy { it.name }

    fun getGroup(id: String): TimeGroup? = groups.firstOrNull { it.id == id }

    fun newGroup(): TimeGroup {
        val g = TimeGroup(id = uid(), name = "新时间组")
        groups.add(g)
        save()
        return g
    }

    fun saveGroup(group: TimeGroup) {
        save()
    }

    fun deleteGroup(id: String) {
        groups.removeMatching { it.id == id }
        save()
    }

    private fun overlaps(start1: String, end1: String, start2: String, end2: String): Boolean =
        minsOf(start1) < minsOf(end2) && minsOf(start2) < minsOf(end1)

    /** 覆盖=这一天整体替换；合并=重叠的原有段删掉，其余保留，再加组里的时间段。 */
    fun applyGroupToDay(group: TimeGroup, dayIdx: Int, overwrite: Boolean) {
        if (overwrite) {
            slots.removeMatching { it.days.size == 1 && it.days.contains(dayIdx) }
        } else {
            group.items.forEach { item ->
                slots.removeMatching { s ->
                    s.days.size == 1 && s.days.contains(dayIdx) &&
                        overlaps(s.start, s.end, item.start, item.end)
                }
            }
        }
        group.items.forEach { item ->
            slots.add(
                Slot(
                    id = uid(),
                    name = item.label.ifBlank { "未知" },
                    type = "class",
                    start = item.start,
                    end = item.end,
                    days = mutableListOf(dayIdx)
                )
            )
        }
        save()
    }

    fun epochNow(): Long = java.time.LocalDate.now().toEpochDay()

    /** 本周某个星期几（周日=0，与 DAY_NAMES / todayIndex 一致）的日号。 */
    fun epochOfThisWeek(dayIdx: Int): Long = epochNow() - todayIndex() + dayIdx

    /** 也和 todayIndex 一样是周日=0（以前这里是周一=0，导致特殊时段错一天）。 */
    fun currentWeekdayIdx(): Int = todayIndex()

    fun specialForDate(epochDay: Long): SpecialRule? = specials.firstOrNull { r ->
        !r.closed && r.startEpochDay > 0L && epochDay >= r.startEpochDay && epochDay <= r.endEpochDay() &&
            (epochDay - r.startEpochDay) % 7L == 0L
    }

    fun activeSpecials(): List<SpecialRule> =
        specials.filter { !it.closed && it.endEpochDay() >= epochNow() }.sortedBy { it.startEpochDay }

    fun newSpecial(weekday: Int): SpecialRule {
        var start = epochOfThisWeek(weekday)
        if (start < epochNow()) start += 7
        val r = SpecialRule(id = uid(), weekday = weekday, startEpochDay = start, weeks = 1)
        specials.add(r)
        save()
        return r
    }

    fun saveSpecial(rule: SpecialRule) {
        save()
    }

    fun closeSpecial(id: String) {
        specials.removeMatching { it.id == id }
        save()
    }

    fun forDay(day: Int): List<Slot> = slots.filter { it.days.isEmpty() || it.days.contains(day) }.sortedBy { minsOf(it.start) }

    fun stateAt(day: Int, nowMin: Double): StateInfo {
        val special = specialForDate(epochOfThisWeek(day))
        val items = if (special != null && special.items.isNotEmpty()) {
            special.items.mapIndexed { i, it ->
                Slot(
                    id = "sp-" + special.id + "-" + i,
                    name = it.label.ifBlank { "特殊" },
                    type = "class",
                    start = it.start,
                    end = it.end
                )
            }
        } else forDay(day)
        var active: Slot? = null
        var activeStart = 0
        var activeEnd = 0
        for (s in items) {
            val st = minsOf(s.start)
            val en = minsOf(s.end)
            if (nowMin >= st && nowMin < en) {
                active = s
                activeStart = st
                activeEnd = en
                break
            }
        }
        var next: Slot? = null
        var nextStart = 0
        for (s in items) {
            val st = minsOf(s.start)
            if (st > nowMin && (next == null || st < nextStart)) {
                next = s
                nextStart = st
            }
        }
        var nextOffset = 0
        if (next == null) {
            val tomorrow = forDay((day + 1) % 7)
            if (tomorrow.isNotEmpty()) {
                next = tomorrow[0]
                nextStart = minsOf(next.start)
                nextOffset = 1
            }
        }
        val remain = if (active != null) activeEnd - nowMin else 0.0
        val progress = if (active != null) ((nowMin - activeStart) / maxOf(1.0, (activeEnd - activeStart).toDouble())).toFloat() else 0f
        val nextIn = next?.let { if (nextOffset == 1) (1440 - nowMin + nextStart) else (nextStart - nowMin) } ?: -1.0
        return StateInfo(active, activeStart, activeEnd, next, nextStart, nextOffset, nextIn, remain, progress)
    }

    fun getSlot(id: String): Slot? = slots.firstOrNull { it.id == id }

    fun saveSlot(draft: Slot): Slot? {
        val target: Slot
        if (draft.id.isNotEmpty()) {
            target = getSlot(draft.id) ?: return null
        } else {
            draft.id = uid()
            slots.add(draft)
            target = draft
        }
        target.name = if (draft.name.trim().isEmpty()) "未命名" else draft.name.trim()
        target.type = draft.type
        target.start = draft.start
        target.end = draft.end
        target.days.clear()
        draft.days.forEach { if (it in 0..6 && !target.days.contains(it)) target.days.add(it) }
        target.days.sort()
        if (minsOf(target.end) <= minsOf(target.start)) {
            var fixed = minsOf(target.start) + 45
            if (fixed > 1439) fixed = 1439
            target.end = fmtMins(fixed)
        }
        save()
        return target
    }

    fun deleteSlot(id: String) {
        slots.removeMatching { it.id == id }
        save()
    }

    fun resetSchedule(saveNow: Boolean = true) {
        slots.clear()
        addSlot("s01", "早读", "other", "07:30", "08:00")
        addSlot("s02", "第一节", "class", "08:00", "08:45")
        addSlot("s03", "第二节", "class", "08:55", "09:40")
        addSlot("s04", "大课间", "rest", "09:40", "10:10")
        addSlot("s05", "第三节", "class", "10:10", "10:55")
        addSlot("s06", "第四节", "class", "11:05", "11:50")
        addSlot("s07", "午饭", "meal", "12:00", "12:45")
        addSlot("s08", "午休", "rest", "12:45", "13:45")
        addSlot("s09", "第五节", "class", "14:00", "14:45")
        addSlot("s10", "第六节", "class", "14:55", "15:40")
        addSlot("s11", "晚饭", "meal", "17:30", "18:15")
        addSlot("s12", "晚自习", "class", "19:00", "21:00")
        if (saveNow) save()
    }

    private fun addSlot(id: String, name: String, type: String, start: String, end: String) {
        slots.add(Slot(id = id, name = name, type = type, start = start, end = end))
    }

    fun resetAll() {
        notes.clear()
        resetSchedule(saveNow = false)
        save()
    }

    fun exportJson(): String = snapshot().toString(2)

    fun importJson(raw: String): Boolean {
        val n = ArrayList<Note>()
        val s = ArrayList<Slot>()
        val g = ArrayList<TimeGroup>()
        val sp = ArrayList<SpecialRule>()
        try {
            collect(JSONObject(raw), n, s, g, sp)
        } catch (e: Exception) {
            EventLog.add("store", "import fail: " + e)
            return false
        }
        if (n.isEmpty() && s.isEmpty() && g.isEmpty() && sp.isEmpty()) return false
        if (s.isEmpty()) {
            resetSchedule(saveNow = false)
            s.addAll(slots)
        }
        applyIn(n, s, g, sp)
        save()
        return true
    }
}