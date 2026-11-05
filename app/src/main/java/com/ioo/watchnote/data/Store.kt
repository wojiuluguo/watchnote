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

        fun typeColor(type: String): androidx.compose.ui.graphics.Color = when (type) {
            "class" -> androidx.compose.ui.graphics.Color(0xFFC8B6FF)
            "meal" -> androidx.compose.ui.graphics.Color(0xFFFFB2C7)
            "rest" -> androidx.compose.ui.graphics.Color(0xFFCAC2DC)
            else -> androidx.compose.ui.graphics.Color(0xFFCBC3CE)
        }

        fun typeBg(type: String): androidx.compose.ui.graphics.Color = when (type) {
            "class" -> androidx.compose.ui.graphics.Color(0xFF493575)
            "meal" -> androidx.compose.ui.graphics.Color(0xFF6D3348)
            "rest" -> androidx.compose.ui.graphics.Color(0xFF484451)
            else -> androidx.compose.ui.graphics.Color(0xFF2C292E)
        }

        fun nowMins(): Double {
            val c = Calendar.getInstance()
            return c.get(Calendar.HOUR_OF_DAY) * 60.0 + c.get(Calendar.MINUTE) + c.get(Calendar.SECOND) / 60.0
        }

        fun todayIndex(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1

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
    }

    fun getAccent(): Int = UiPrefsState.accent.value

    fun setAccent(value: Int) {
        UiPrefsState.accent.value = value
        prefs.edit().putInt("accent", value).apply()
        EventLog.add("setting", "accent=" + value)
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
        UiPrefsState.animMode.value = prefs.getInt("anim_mode", 0)
        UiPrefsState.haptics.value = prefs.getBoolean("haptics", true)
        // 修复旧数据里的空 id，并写回，避免重复 key 闪退
        save()
    }

    private fun load() {
        notes.clear()
        slots.clear()
        val raw = prefs.getString("data_json", null)
        if (raw != null) {
            try {
                val root = JSONObject(raw)
                val na = root.optJSONArray("notes")
                if (na != null) {
                    for (i in 0 until na.length()) {
                        val o = na.getJSONObject(i)
                        notes.add(
                            Note(
                                id = o.optString("id", uid()),
                                text = o.optString("text", ""),
                                tag = o.optString("tag", "随手记"),
                                marked = o.optBoolean("marked", false),
                                done = o.optBoolean("done", false),
                                audioPath = o.optString("audioPath", ""),
                                audioDur = o.optLong("audioDur", 0),
                                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                            )
                        )
                    }
                }
                val sa = root.optJSONArray("slots")
                if (sa != null) {
                    for (i in 0 until sa.length()) {
                        val o = sa.getJSONObject(i)
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
                        slots.add(s)
                    }
                }
                val ga = root.optJSONArray("groups")
                if (ga != null) {
                    for (i in 0 until ga.length()) {
                        val o = ga.getJSONObject(i)
                        val g = TimeGroup(
                            id = o.optString("id", "").ifBlank { uid() },
                            name = o.optString("name", "时间组")
                        )
                        val ia = o.optJSONArray("items")
                        if (ia != null) for (j in 0 until ia.length()) {
                            val io = ia.getJSONObject(j)
                            g.items.add(
                                GroupItem(
                                    start = io.optString("start", "08:00"),
                                    end = io.optString("end", "08:45"),
                                    label = io.optString("label", "")
                                )
                            )
                        }
                        groups.add(g)
                    }
                }
                val spa = root.optJSONArray("specials")
                if (spa != null) {
                    for (i in 0 until spa.length()) {
                        val o = spa.getJSONObject(i)
                        val r = SpecialRule(
                            id = o.optString("id", "").ifBlank { uid() },
                            weekday = o.optInt("weekday", 0),
                            startEpochDay = o.optLong("start", 0L),
                            weeks = o.optInt("weeks", 1),
                            closed = o.optBoolean("closed", false)
                        )
                        val ia = o.optJSONArray("items")
                        if (ia != null) for (j in 0 until ia.length()) {
                            val io = ia.getJSONObject(j)
                            r.items.add(
                                GroupItem(
                                    start = io.optString("start", "08:00"),
                                    end = io.optString("end", "08:45"),
                                    label = io.optString("label", "")
                                )
                            )
                        }
                        specials.add(r)
                    }
                }
            } catch (e: Exception) {
                notes.clear()
                slots.clear()
            }
        }
        if (slots.isEmpty()) resetSchedule(saveNow = false)
        save()
    }

    fun save() {
        try {
            val root = JSONObject()
            val na = JSONArray()
            notes.forEach { n ->
                val o = JSONObject()
                o.put("id", n.id)
                o.put("text", n.text)
                o.put("tag", n.tag)
                o.put("marked", n.marked)
                o.put("done", n.done)
                o.put("audioPath", n.audioPath)
                o.put("audioDur", n.audioDur)
                o.put("createdAt", n.createdAt)
                o.put("updatedAt", n.updatedAt)
                na.put(o)
            }
            val sa = JSONArray()
            slots.forEach { s ->
                val o = JSONObject()
                o.put("id", s.id)
                o.put("name", s.name)
                o.put("type", s.type)
                o.put("start", s.start)
                o.put("end", s.end)
                val da = JSONArray()
                s.days.forEach { da.put(it) }
                o.put("days", da)
                sa.put(o)
            }
            val ga = JSONArray()
            groups.forEach { g ->
                val o = JSONObject()
                o.put("id", g.id)
                o.put("name", g.name)
                val ia = JSONArray()
                g.items.forEach { item ->
                    val io = JSONObject()
                    io.put("start", item.start)
                    io.put("end", item.end)
                    io.put("label", item.label)
                    ia.put(io)
                }
                o.put("items", ia)
                ga.put(o)
            }
            val spa = JSONArray()
            specials.forEach { r ->
                val o = JSONObject()
                o.put("id", r.id)
                o.put("weekday", r.weekday)
                o.put("start", r.startEpochDay)
                o.put("weeks", r.weeks)
                o.put("closed", r.closed)
                val ia = JSONArray()
                r.items.forEach { item ->
                    val io = JSONObject()
                    io.put("start", item.start)
                    io.put("end", item.end)
                    io.put("label", item.label)
                    ia.put(io)
                }
                o.put("items", ia)
                spa.put(o)
            }
            root.put("notes", na)
            root.put("slots", sa)
            root.put("groups", ga)
            root.put("specials", spa)
            prefs.edit().putString("data_json", root.toString()).apply()
            version.intValue = version.intValue + 1
        } catch (e: Exception) {
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
            val hasContent = draft.text.trim().isNotEmpty() || draft.audioPath.isNotEmpty()
            if (!hasContent) return null
            draft.id = uid()
            notes.add(draft)
            target = draft
        }
        target.text = draft.text
        target.tag = draft.tag
        target.marked = draft.marked
        target.done = draft.done
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

    fun epochNow(): Long = System.currentTimeMillis() / 86_400_000L

    fun epochOfThisWeek(dayIdx: Int): Long {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val todayIdx = (dow + 5) % 7
        return epochNow() - todayIdx + dayIdx
    }

    fun currentWeekdayIdx(): Int {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return (dow + 5) % 7
    }

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

    fun exportJson(): String {
        val root = JSONObject()
        val na = JSONArray()
        notes.forEach { n ->
            val o = JSONObject()
            o.put("id", n.id); o.put("text", n.text); o.put("tag", n.tag)
            o.put("marked", n.marked); o.put("done", n.done)
            o.put("audioPath", n.audioPath); o.put("audioDur", n.audioDur)
            o.put("createdAt", n.createdAt); o.put("updatedAt", n.updatedAt)
            na.put(o)
        }
        val sa = JSONArray()
        slots.forEach { s ->
            val o = JSONObject()
            o.put("id", s.id); o.put("name", s.name); o.put("type", s.type)
            o.put("start", s.start); o.put("end", s.end)
            val da = JSONArray(); s.days.forEach { da.put(it) }
            o.put("days", da)
            sa.put(o)
        }
        root.put("notes", na); root.put("slots", sa)
        return root.toString(2)
    }

    fun importJson(raw: String): Boolean {
        return try {
            val root = JSONObject(raw)
            val na = root.optJSONArray("notes") ?: return false
            notes.clear()
            for (i in 0 until na.length()) {
                val o = na.getJSONObject(i)
                notes.add(Note(
                    id = o.optString("id", uid()),
                    text = o.optString("text", ""),
                    tag = o.optString("tag", "随手记"),
                    marked = o.optBoolean("marked", false),
                    done = o.optBoolean("done", false),
                    audioPath = o.optString("audioPath", ""),
                    audioDur = o.optLong("audioDur", 0),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                ))
            }
            val sa = root.optJSONArray("slots")
            if (sa != null) {
                slots.clear()
                for (i in 0 until sa.length()) {
                    val o = sa.getJSONObject(i)
                    val s = Slot(
                        id = o.optString("id", uid()),
                        name = o.optString("name", "未命名"),
                        type = o.optString("type", "class"),
                        start = o.optString("start", "08:00"),
                        end = o.optString("end", "08:45")
                    )
                    val da = o.optJSONArray("days")
                    if (da != null) for (j in 0 until da.length()) {
                        val d = da.optInt(j, -1)
                        if (d in 0..6) s.days.add(d)
                    }
                    slots.add(s)
                }
            }
            if (slots.isEmpty()) resetSchedule(saveNow = false)
            save()
            true
        } catch (e: Exception) {
            false
        }
    }
}