package com.ioo.watchnote.plugin

import com.ioo.watchnote.Note
import com.ioo.watchnote.Store
import com.ioo.watchnote.plugin.builtin.ThemePlugin
import com.ioo.watchnote.plugin.external.EntryDigest
import com.ioo.watchnote.plugin.external.ExternalInstallPolicy
import com.ioo.watchnote.plugin.external.ExternalPluginSignature
import com.ioo.watchnote.plugin.external.ExternalSignatureResult
import com.ioo.watchnote.plugin.external.ExternalManifestResult
import com.ioo.watchnote.plugin.external.ExternalPackageResult
import com.ioo.watchnote.plugin.external.ExternalPluginManifestParser
import com.ioo.watchnote.plugin.external.ExternalPackageChecker
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * 一条自测结果。界面上直接按顺序显示，不许抛异常出去。
 */
data class PluginSelfTestResult(val name: String, val ok: Boolean, val detail: String)

/**
 * 插件系统自测：不碰正在跑的那份 PluginCore，单独建一个沙盒核心跑一遍生命周期，
 * 跑完就扔，所以随便装、随便卸，不会影响用户已经装着的主题插件或任何设置。
 *
 * 覆盖的是 API 合同里最容易被写错的东西：命名空间前缀、页面上限、按钮插槽上限、
 * 卸载残留、安装失败回滚、minApi、权限、事件隔离。
 */
object PluginSelfTest {

    private class ProbePlugin(
        override val manifest: PluginManifest,
        private val body: (PluginHost) -> Unit,
    ) : WristPlugin {
        override fun install(host: PluginHost) { body(host) }
        override fun uninstall() {}
    }

    private fun manifestOf(id: String, minApi: Int = PluginApi.API_LEVEL, permissions: Set<Permission> = emptySet()) =
        PluginManifest(id = id, name = "自测插件", version = 1, minApi = minApi, permissions = permissions)

    private fun page(id: String): PageSpec = PageSpec(id = id, title = "自测页", content = { _, _ -> })

    private fun button(id: String, slot: ButtonSlot): ButtonSpec =
        ButtonSpec(id = id, slot = slot, icon = "•", label = "自测按钮", onClick = {})

    fun run(
        store: Store,
        externalSelfTest: (() -> String?)? = null,
        externalSelfTestInstall: (() -> String?)? = null,
        externalPageSelfTestInstall: (() -> String?)? = null,
    ): List<PluginSelfTestResult> {
        val results = ArrayList<PluginSelfTestResult>()
        fun check(name: String, block: () -> String?) {
            val error = try {
                block()
            } catch (t: Throwable) {
                "抛异常：" + (t.message ?: t.javaClass.simpleName)
            }
            results.add(PluginSelfTestResult(name = name, ok = error == null, detail = error ?: "通过"))
        }

        // 1. 内置插件能装，两页都注册上了
        check("安装主题插件并注册两页") {
            val core = PluginCore(store)
            val r = core.install(ThemePlugin(store) {})
            if (r !is RegisterResult.Ok) return@check "安装失败：" + (r as RegisterResult.Rejected).reason
            if (!core.isInstalled(ThemePlugin.PLUGIN_ID)) return@check "安装成功但 isInstalled 是 false"
            if (core.pages().size != 2) return@check "页面数应为 2，实际 " + core.pages().size
            if (core.ownerOfPage(ThemePlugin.PAGE_LIST) != ThemePlugin.PLUGIN_ID) return@check "ownerOfPage 查不到"
            null
        }

        // 2. 同一 id 不许装第二次
        check("重复安装被拒绝") {
            val core = PluginCore(store)
            core.install(ThemePlugin(store) {})
            val again = core.install(ThemePlugin(store) {})
            if (again is RegisterResult.Ok) "第二次安装居然成功了" else null
        }

        // 3. 页面 id 必须带自己的插件前缀，防止抢别人的页面
        check("页面 id 必须带插件前缀") {
            val core = PluginCore(store)
            var captured: RegisterResult? = null
            val p = ProbePlugin(manifestOf("test.prefix")) { host ->
                captured = host.pages.add(page(id = "test.other:page"))
            }
            core.install(p)
            val r = captured
            if (r is RegisterResult.Rejected) null else "没带前缀的页面被接受了"
        }

        // 4. 每插件最多 3 页
        check("每插件最多 3 页") {
            val core = PluginCore(store)
            var fourth: RegisterResult? = null
            val p = ProbePlugin(manifestOf("test.limit")) { host ->
                repeat(3) { i -> host.pages.add(page("test.limit:p" + i)) }
                fourth = host.pages.add(page("test.limit:p3"))
            }
            core.install(p)
            val r = fourth
            if (r is RegisterResult.Rejected) null else "第 4 页被接受了"
        }

        // 5. 每个插槽最多 2 个按钮，且宿主查询能按 owner + 插槽拿到
        check("按钮插槽上限 2 且可查询") {
            val core = PluginCore(store)
            var third: RegisterResult? = null
            val p = ProbePlugin(manifestOf("test.button")) { host ->
                host.buttons.add(button("test.button:b1", ButtonSlot.PAGE_BOTTOM))
                host.buttons.add(button("test.button:b2", ButtonSlot.PAGE_BOTTOM))
                third = host.buttons.add(button("test.button:b3", ButtonSlot.PAGE_BOTTOM))
            }
            core.install(p)
            val r = third
            if (r !is RegisterResult.Rejected) return@check "第 3 个按钮被接受了"
            val got = core.buttonsOf("test.button", ButtonSlot.PAGE_BOTTOM)
            if (got.size != 2) return@check "按 owner+插槽查到 " + got.size + " 个，应为 2"
            if (core.buttonsOf(ButtonSlot.PAGE_BOTTOM).none { it.ownerPluginId == "test.button" }) return@check "按插槽查不到"
            null
        }

        // 6. 卸载后页面/按钮/清单/kv 全部清干净
        check("卸载不留痕迹") {
            val core = PluginCore(store)
            val p = ProbePlugin(manifestOf("test.uninstall")) { host ->
                host.pages.add(page("test.uninstall:p"))
                host.buttons.add(button("test.uninstall:b", ButtonSlot.SETTINGS_ROW))
                host.data.kv().putString("k", "v")
            }
            core.install(p)
            if (!core.uninstall("test.uninstall")) return@check "卸载返回 false"
            if (core.pages().isNotEmpty()) return@check "还剩 " + core.pages().size + " 页"
            if (core.buttons().isNotEmpty()) return@check "还剩 " + core.buttons().size + " 个按钮"
            if (core.manifests.isNotEmpty()) return@check "插件清单没清掉"
            if (core.isInstalled("test.uninstall")) return@check "isInstalled 还是 true"
            core.install(p)
            val rePages = core.pages()
            if (rePages.size != 1) return@check "重装后页面数应为 1，实际 " + rePages.size
            null
        }

        // 7. install 里抛异常 = 整体回滚，宿主状态不变
        check("安装抛异常会整体回滚") {
            val core = PluginCore(store)
            val broken = ProbePlugin(manifestOf("test.broken")) { host ->
                host.pages.add(page("test.broken:p"))
                host.buttons.add(button("test.broken:b", ButtonSlot.CARD_ACTION))
                throw IllegalStateException("故意炸一个")
            }
            val r = core.install(broken)
            if (r is RegisterResult.Ok) return@check "抛异常的插件居然装上了"
            if (core.pages().isNotEmpty()) return@check "回滚不干净：还剩页面"
            if (core.buttons().isNotEmpty()) return@check "回滚不干净：还剩按钮"
            null
        }

        // 8. minApi 高于宿主 API 必须拒绝
        check("minApi 高于宿主被拒绝") {
            val core = PluginCore(store)
            val r = core.install(ProbePlugin(manifestOf("test.api", minApi = PluginApi.API_LEVEL + 1)) { })
            if (r is RegisterResult.Ok) "高版本 API 的插件被接受了" else null
        }

        // 9. 没申请 READ_NOTES 就调 notes() 必须抛异常，而不是静默给空表
        check("越权读笔记会抛异常") {
            val core = PluginCore(store)
            var message: String? = null
            val p = ProbePlugin(manifestOf("test.perm")) { host ->
                try {
                    host.data.notes()
                } catch (t: Throwable) {
                    message = t.message
                }
            }
            core.install(p)
            val m = message
            if (m == null) "没有抛异常" else if (!m.contains("READ_NOTES")) "异常信息不对：" + m else null
        }

        // 10. 一个事件监听器抛异常，不能影响别的监听器
        check("事件监听器崩溃隔离") {
            val core = PluginCore(store)
            var secondCalled = false
            core.events().onPageShown { throw IllegalStateException("故意炸一个") }
            core.events().onPageShown { secondCalled = true }
            core.notifyPageShown("whatever")
            if (!secondCalled) "第一个监听器抛错后，第二个没被调用" else null
        }

        // 11. 管理页「关掉」（disable）要把页面/按钮收走，但插件自己的 kv 数据必须留着
        check("管理页关掉保留插件数据") {
            val core = PluginCore(store)
            val writer = ProbePlugin(manifestOf("test.disable")) { host ->
                host.pages.add(page("test.disable:p"))
                host.data.kv().putString("k", "v")
            }
            core.install(writer)
            if (!core.disable("test.disable")) return@check "disable 返回 false"
            if (core.isInstalled("test.disable")) return@check "disable 后还装着"
            if (core.pages().isNotEmpty()) return@check "disable 后还剩页面"
            if (core.manifests.isNotEmpty()) return@check "disable 后清单没清"
            var readBack: String? = null
            val reader = ProbePlugin(manifestOf("test.disable")) { host -> readBack = host.data.kv().getString("k") }
            core.install(reader)
            if (readBack != "v") "关掉再打开数据丢了：" + readBack
            else null
        }

        // 12. 真卸载（uninstall）必须清空插件 kv，不留垃圾
        check("真卸载清空插件数据") {
            val core = PluginCore(store)
            val writer = ProbePlugin(manifestOf("test.uninstall.kv")) { host ->
                host.data.kv().putString("k", "v")
            }
            core.install(writer)
            core.uninstall("test.uninstall.kv")
            var readBack: String? = null
            val reader = ProbePlugin(manifestOf("test.uninstall.kv")) { host -> readBack = host.data.kv().getString("k") }
            core.install(reader)
            if (readBack != null) "卸载后数据还在：" + readBack
            else null
        }

        // 13. 插件只能打开自己的页面：host.openPage 不能借道打开别的插件的页面
        check("插件不能打开别人的页面") {
            val core = PluginCore(store)
            var opened: String? = null
            core.navigateHandler = { pageId -> opened = pageId }
            core.install(ProbePlugin(manifestOf("test.owner.a")) { host ->
                host.pages.add(page("test.owner.a:p"))
            })
            core.install(ProbePlugin(manifestOf("test.owner.b")) { host ->
                host.openPage("test.owner.a:p")
            })
            if (opened != null) return@check "别的插件的页面被打开了：" + opened
            null
        }

        // 14. 重新加载（disable + install）保留数据，并把页面原样装回来
        check("重新加载保留插件数据") {
            val core = PluginCore(store)
            var firstHost: PluginHost? = null
            core.install(ProbePlugin(manifestOf("test.reload")) { host ->
                firstHost = host
                host.pages.add(page("test.reload:p"))
            })
            firstHost?.data?.kv()?.putInt("keep", 5)
            var readBack = 0
            val result = core.reload(ProbePlugin(manifestOf("test.reload")) { host ->
                readBack = host.data.kv().getInt("keep", 0)
                host.pages.add(page("test.reload:p"))
            })
            if (result !is RegisterResult.Ok) return@check "重新加载失败：" + result
            if (core.pagesOf("test.reload").size != 1) return@check "重新加载后页面数不是 1"
            if (readBack != 5) "重新加载后数据丢了：" + readBack
            else null
        }

        // 15. CARD_ACTION 必须说明「点的是哪条笔记」，没有 onCardAction 直接拒绝
        check("卡片按钮必须提供 onCardAction") {
            val core = PluginCore(store)
            var rejected: String? = null
            val p = ProbePlugin(manifestOf("test.card.need")) { host ->
                val r = host.buttons.add(button("test.card.need:b", ButtonSlot.CARD_ACTION))
                if (r is RegisterResult.Rejected) rejected = r.reason
            }
            core.install(p)
            if (rejected == null) "没带 onCardAction 的卡片按钮被接受了" else null
        }

        // 16. 卡片动作把被点的那条笔记原样交给插件
        check("卡片动作收到被点的笔记") {
            val core = PluginCore(store)
            var got: Note? = null
            val p = ProbePlugin(manifestOf("test.card.run")) { host ->
                host.buttons.add(
                    ButtonSpec(
                        id = "test.card.run:b",
                        slot = ButtonSlot.CARD_ACTION,
                        icon = "ⓘ",
                        label = "详情",
                        onClick = {},
                        onCardAction = { got = it },
                    )
                )
            }
            core.install(p)
            val btn = core.buttonsOf(ButtonSlot.CARD_ACTION).firstOrNull { it.ownerPluginId == "test.card.run" }
                ?: return@check "没查到注册的卡片按钮"
            val note = Note(id = "selftest-card", text = "自测笔记")
            core.runCardAction(btn, note)
            val actual = got ?: return@check "卡片回调没被调用"
            if (actual.id != "selftest-card") "回调收到的不是那条笔记：" + actual.id else null
        }

        // 17. 内置插件目录整体健康：id 唯一、manifest.id 与目录一致、全部能装、不超上限
        //     这条是给「插件变多」准备的：新加一个内置插件写错，自测页立刻变红，不用等用户装上才发现。
        check("内置插件目录全部可安装") {
            val core = PluginCore(store)
            val ids = HashSet<String>()
            BuiltinPlugins.all.forEach { entry ->
                if (!ids.add(entry.id)) return@check "插件 id 重复：" + entry.id
                val plugin = entry.build(store) {}
                if (plugin.manifest.id != entry.id) {
                    return@check entry.name + " 的 manifest.id（" + plugin.manifest.id + "）与目录 id 不一致"
                }
                when (val r = core.install(plugin)) {
                    is RegisterResult.Ok -> Unit
                    is RegisterResult.Rejected -> return@check entry.name + " 装不上：" + r.reason
                }
                if (core.pagesOf(entry.id).size > MAX_PAGES_PER_PLUGIN) {
                    return@check entry.name + " 注册页数超过上限"
                }
                if (core.buttonsOfPlugin(entry.id).size > MAX_BUTTONS_PER_SLOT * ButtonSlot.values().size) {
                    return@check entry.name + " 注册按钮数超过上限"
                }
            }
            val pageIds = core.pages().map { it.spec.id }
            if (pageIds.size != pageIds.distinct().size) return@check "内置插件之间有页面 id 冲突"
            null
        }

        // 19. 插件能引用官方页面：宿主收到白名单里的 HostScreen；宿主没接导航时只提示不崩
        check("插件能引用官方页面") {
            val core = PluginCore(store)
            var got: HostScreen? = null
            core.hostNavigateHandler = { got = it }
            core.install(
                ProbePlugin(manifestOf("test.hostnav")) { host ->
                    host.openHostPage(HostScreen.SELF_TEST)
                }
            )
            if (got != HostScreen.SELF_TEST) return@check "宿主没收到官方页面跳转，实际 " + got
            // 宿主没接导航：只提示，不许抛异常（插件不应该因为宿主没接好就崩）
            val bare = PluginCore(store)
            var toasted: String? = null
            bare.toastHandler = { toasted = it }
            bare.install(
                ProbePlugin(manifestOf("test.hostnav2")) { host ->
                    host.openHostPage(HostScreen.ABOUT)
                }
            )
            if (toasted == null) "宿主没接导航时什么都没提示" else null
        }

        // 18. 列表页是另外一种页面类型：注册接口独立、存进去类型对、卸载一起收走
        //     这条盯的是「两种页面别串味」：列表页要是被当成内容页存，宿主就不会走懒加载分支。
        check("列表页是单独一种页面类型") {
            val core = PluginCore(store)
            val p = ProbePlugin(manifestOf("test.list")) { host ->
                host.pages.add(
                    ListPageSpec(id = "test.list:tags", title = "分类分布") { list, _, _ ->
                        list.item { }
                        list.items(3) { }
                    }
                )
            }
            when (val r = core.install(p)) {
                is RegisterResult.Ok -> Unit
                is RegisterResult.Rejected -> return@check "列表页没装上：" + r.reason
            }
            val spec = core.page("test.list:tags") ?: return@check "注册后查不到列表页"
            if (spec !is ListPageSpec) return@check "列表页被存成了别的类型：" + spec.kind
            if (spec.kind != PageKind.LIST) return@check "列表页的 kind 不是 LIST：" + spec.kind
            core.uninstall("test.list")
            if (core.page("test.list:tags") != null) "卸载后列表页还留着" else null
        }

        // 20. 外置插件清单：v2 加载还没做，但契约先钉死，免得以后改口径
        check("外置插件清单校验") {
            val good = """
                {
                  "schemaVersion": 1,
                  "id": "com.example.hello",
                  "name": "你好",
                  "version": 1,
                  "minApi": 1,
                  "entryClass": "com.example.hello.HelloPlugin",
                  "permissions": ["READ_NOTES"],
                  "author": "自测"
                }
            """.trimIndent()
            val parsed = ExternalPluginManifestParser.parse(good)
            if (parsed !is ExternalManifestResult.Valid) {
                return@check "合法清单被拒：" + (parsed as ExternalManifestResult.Invalid).reason
            }
            if (parsed.parsed.manifest.id != "com.example.hello") return@check "id 解析不对"
            if (parsed.parsed.manifest.permissions != setOf(Permission.READ_NOTES)) return@check "权限解析不对"
            if (parsed.parsed.entryClass != "com.example.hello.HelloPlugin") return@check "entryClass 解析不对"
            // 坏清单一律用 JSONObject 改字段生成，不再靠 replace 整行文本：
            // 以前删 entryClass 是拿 trimIndent 之前的 18 空格缩进去匹配，trimIndent 之后只剩 2 空格，
            // 结果一行都没删掉、清单仍然合法，自测就报「缺 entryClass 的清单居然通过了」（4.1.16 的假失败）。
            // 改成 JSON 层面改字段后，加字段或改缩进都不会再骗自己。
            val goodObj = try {
                JSONObject(good)
            } catch (t: Throwable) {
                return@check "自测用的合法清单本身不是合法 JSON：" + (t.message ?: "格式错误")
            }
            fun variant(block: JSONObject.() -> Unit): String =
                JSONObject(goodObj.toString()).apply(block).toString()
            val badCases = linkedMapOf(
                "坏 id" to variant { put("id", "Hello World") },
                "保留 id" to variant { put("id", "com.ioo.watchnote.fake") },
                "未知权限" to variant { put("permissions", JSONArray().put("READ_EVERYTHING")) },
                "minApi 过高" to variant { put("minApi", 999) },
                "清单版本" to variant { put("schemaVersion", 2) },
                "缺 entryClass" to variant { remove("entryClass") },
                "空 entryClass" to variant { put("entryClass", "") },
                "entryClass 不是完整类名" to variant { put("entryClass", "HelloPlugin") },
                "缺 name" to variant { remove("name") },
                "version 是 0" to variant { put("version", 0) },
                "缺 schemaVersion" to variant { remove("schemaVersion") },
                "permissions 不是数组" to variant { put("permissions", "READ_NOTES") },
                "有签名算法没公钥" to variant { put("signingAlgorithm", "SHA256withECDSA") },
            )
            for ((label, json) in badCases) {
                val r = ExternalPluginManifestParser.parse(json)
                if (r !is ExternalManifestResult.Invalid) return@check label + " 的清单居然通过了"
                if (r.reason.isBlank()) return@check label + " 被拒但没说明原因"
            }
            null
        }

        // 21. 外置插件包结构：解压前就要挡住越界路径、缺清单、缺代码、不认识的文件
        check("外置插件包结构校验") {
            fun writeZip(target: File, entries: Map<String, ByteArray>) {
                ZipOutputStream(target.outputStream().buffered()).use { zos ->
                    for ((name, bytes) in entries) {
                        zos.putNextEntry(ZipEntry(name))
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                }
            }

            val manifestJson = """
                {
                  "schemaVersion": 1,
                  "id": "com.example.hello",
                  "name": "你好",
                  "version": 1,
                  "minApi": 1,
                  "entryClass": "com.example.hello.HelloPlugin"
                }
            """.trimIndent()
            val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)
            val fakeDex = byteArrayOf(0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x35, 0x00)
            val parent = File.createTempFile("wplugin", ".tmp").parentFile ?: return@check "拿不到临时目录"
            val work = File(parent, "wplugin-selftest-" + System.nanoTime())
            if (!work.mkdirs()) return@check "建不了临时目录"
            try {
                val good = File(work, "good.wplugin")
                writeZip(good, mapOf("plugin.json" to manifestBytes, "classes.dex" to fakeDex))
                val goodResult = ExternalPackageChecker.inspect(good)
                if (goodResult !is ExternalPackageResult.Valid) {
                    return@check "合法包被拒：" + (goodResult as ExternalPackageResult.Invalid).reason
                }
                if (goodResult.pkg.manifest.manifest.id != "com.example.hello") return@check "清单 id 解析不对"
                if (goodResult.pkg.dexEntries.size != 1) return@check "代码文件数量不对：" + goodResult.pkg.dexEntries.size

                val badCases = LinkedHashMap<String, Map<String, ByteArray>>()
                badCases["越界路径"] = mapOf("plugin.json" to manifestBytes, "../evil.dex" to fakeDex)
                badCases["缺清单"] = mapOf("classes.dex" to fakeDex)
                badCases["缺代码"] = mapOf("plugin.json" to manifestBytes)
                badCases["不认识的文件"] = mapOf(
                    "plugin.json" to manifestBytes,
                    "classes.dex" to fakeDex,
                    "run.sh" to byteArrayOf(0x65, 0x63, 0x68, 0x6f),
                )
                var index = 0
                for ((label, entries) in badCases) {
                    index++
                    val file = File(work, "bad-" + index + ".wplugin")
                    writeZip(file, entries)
                    val result = ExternalPackageChecker.inspect(file)
                    if (result !is ExternalPackageResult.Invalid) return@check label + " 的包居然通过了"
                    if (result.reason.isBlank()) return@check label + " 被拒但没说明原因"
                }

                val junk = File(work, "junk.wplugin")
                junk.writeText("definitely not a zip")
                if (ExternalPackageChecker.inspect(junk) !is ExternalPackageResult.Invalid) {
                    return@check "不是 zip 的文件居然通过了"
                }
                null
            } finally {
                work.deleteRecursively()
            }
        }

        // 22. 熔断：连续出错 3 次自动停用；页面/按钮收走，私有数据保留，能再手动开回来
        check("连续出错自动停用") {
            val core = PluginCore(store)
            var toasts = 0
            core.toastHandler = { toasts++ }
            core.install(
                ProbePlugin(manifestOf("test.breaker")) { host ->
                    host.data.kv().putString("k", "v")
                    host.buttons.add(
                        ButtonSpec(
                            id = "test.breaker:boom",
                            slot = ButtonSlot.PAGE_BOTTOM,
                            icon = "!",
                            label = "炸",
                            onClick = { throw IllegalStateException("故意炸") },
                        )
                    )
                }
            )
            val boom = core.buttonsOf("test.breaker", ButtonSlot.PAGE_BOTTOM).firstOrNull()
                ?: return@check "没找到会抛错的按钮"
            if (core.recordFailure("test.breaker", "自测")) return@check "第 1 次失败就停用了"
            core.runButton(boom)
            if (!core.isInstalled("test.breaker")) return@check "第 2 次失败就停用了（阈值应为 3）"
            core.runButton(boom)
            if (core.isInstalled("test.breaker")) return@check "连续 3 次出错后插件还装着"
            if (core.pagesOf("test.breaker").isNotEmpty()) return@check "自动停用后页面没收回"
            if (toasts == 0) return@check "自动停用时没提示"
            var readBack: String? = null
            core.install(
                ProbePlugin(manifestOf("test.breaker")) { host ->
                    readBack = host.data.kv().getString("k")
                }
            )
            if (readBack != "v") "自动停用把插件数据清了：" + readBack else null
        }

        // 23. 外置插件升级策略：降级拒绝、同版本算重装、升级放行
        check("外置插件升级版本策略") {
            if (ExternalInstallPolicy.checkVersion(null, 1) != null) return@check "首次安装被拒"
            if (ExternalInstallPolicy.checkVersion(2, 3) != null) return@check "升级被拒"
            if (ExternalInstallPolicy.checkVersion(2, 2) != null) return@check "同版本重装被拒"
            if (ExternalInstallPolicy.checkVersion(2, 1) == null) return@check "降级居然放行"
            if (ExternalInstallPolicy.label(null, 1) != "首次安装") return@check "label 首装不对"
            null
        }

        // 24. 作者签名：真签名能过、改一个文件就拒、半签名（只有一边）也拒
        check("作者签名能验真也能抓篡改") {
            val keyPair = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
            val pub = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
            val entries = listOf(
                EntryDigest("classes.dex", 7L, ExternalPluginSignature.sha256Hex(byteArrayOf(1, 2, 3, 4, 5, 6, 7))),
                EntryDigest("plugin.json", 2L, ExternalPluginSignature.sha256Hex("{}".toByteArray())),
            )
            val sig = ExternalPluginSignature.signForTest(entries, keyPair.private)
            val ok = ExternalPluginSignature.verify(entries, ExternalPluginSignature.ALGORITHM, pub, sig)
            if (ok !is ExternalSignatureResult.Valid) return@check "正常签名没验过"
            val expected = ExternalPluginSignature.fingerprintOfPublicKey(pub) ?: return@check "算不出公钥指纹"
            if (ok.keyFingerprint != expected) return@check "验签返回的指纹和公钥算的不一致"
            val tampered = entries.map {
                if (it.name == "classes.dex") it.copy(sha256 = ExternalPluginSignature.sha256Hex(byteArrayOf(9))) else it
            }
            if (ExternalPluginSignature.verify(tampered, ExternalPluginSignature.ALGORITHM, pub, sig)
                !is ExternalSignatureResult.Invalid
            ) {
                return@check "改过文件摘要居然还验过"
            }
            if (ExternalPluginSignature.verify(entries, null, null, null) !is ExternalSignatureResult.Unsigned) {
                return@check "没签名的包没判成 Unsigned"
            }
            if (ExternalPluginSignature.verify(entries, ExternalPluginSignature.ALGORITHM, pub, null)
                !is ExternalSignatureResult.Invalid
            ) {
                return@check "有公钥没签名居然合法"
            }
            if (ExternalPluginSignature.verify(entries, ExternalPluginSignature.ALGORITHM, null, sig)
                !is ExternalSignatureResult.Invalid
            ) {
                return@check "有签名没公钥居然合法"
            }
            null
        }

        // 25. 换签名钥匙：已装是签名版，新包换钥匙或没签名都要拒；未签名升签名放行
        check("换签名钥匙被拒绝") {
            if (ExternalInstallPolicy.checkSigning(null, "AAAA") != null) return@check "首装带签名被拒"
            if (ExternalInstallPolicy.checkSigning("AAAA", "AAAA") != null) return@check "同一把钥匙被拒"
            if (ExternalInstallPolicy.checkSigning("AAAA", null) == null) return@check "签名版被没签名的包覆盖居然放行"
            if (ExternalInstallPolicy.checkSigning("AAAA", "BBBB") == null) return@check "换了钥匙居然放行"
            if (ExternalInstallPolicy.signingLabel(null) != "未签名") return@check "未签名标签不对"
            if (!ExternalInstallPolicy.signingLabel("AAAA").contains("AAAA")) return@check "签名标签没带指纹"
            null
        }

        // 26. 外置插件真加载：内置示例包（和手机传的是同一个 .wplugin）解压后必须能被 DexClassLoader 加载。
        //     这条补的是 4.1.18 之前的空洞：以前只校验包结构、从不加载外置 dex，
        //     所以「自测全过、真机装示例包却报插件加载失败」可以同时成立。
        check("外置插件示例包真加载") {
            val hook = externalSelfTest ?: return@check "没有可用的外置插件安装器"
            hook.invoke()
        }

        // 27. 外置插件真装一遍：示例包必须完整走完「解压 → 加载 → 注册 → 写记录」。
        //     第 26 项只加载不注册，所以「加载全过、注册被拒」照样能全绿 —— 这条补上那个盲区。
        check("外置插件示例包真装一遍") {
            val hook = externalSelfTestInstall ?: return@check "没有可用的外置插件安装器"
            hook.invoke()
        }

        // 28. 外置插件 ABI：.wplugin 是独立编译的 dex，里面写死了宿主必须存在的类名。
        //     release 版一旦被 R8 改名或删掉（4.1.19 的 kotlin.Unit -> m32 就是这么炸的），
        //     插件在真机上就报 Class not found using the boot class loader。
        //     这条把「只有装插件才炸」的问题变成自测里一眼能看见的失败。
        check("外置插件运行时要用的类名没被混淆") {
            val required = listOf(
                "kotlin.Unit",
                "kotlin.jvm.functions.Function0",
                "kotlin.jvm.functions.Function1",
                "kotlin.jvm.internal.Intrinsics",
                "com.ioo.watchnote.plugin.SimplePage",
                "com.ioo.watchnote.plugin.SimpleAction",
                "com.ioo.watchnote.plugin.SimpleRow",
                "com.ioo.watchnote.plugin.PageKind",
                "com.ioo.watchnote.plugin.PageRegistry",
                "com.ioo.watchnote.plugin.WristPlugin",
                "com.ioo.watchnote.plugin.PluginHost",
                "com.ioo.watchnote.plugin.ButtonSpec",
                "com.ioo.watchnote.plugin.ButtonSlot",
                "com.ioo.watchnote.plugin.PluginManifest",
                "com.ioo.watchnote.plugin.ButtonRegistry",
            )
            val missing = required.filter { name ->
                runCatching {
                    Class.forName(name, false, PluginSelfTest::class.java.classLoader)
                }.isFailure
            }
            if (missing.isEmpty()) null else "这些类被改名或删掉了：" + missing.joinToString("、")
        }

        // 29. 声明式页面：不写 Compose 的插件（Java + d8）也能贡献页面，行由宿主用官方组件画。
        //     这条盯的是「页面 API 别只有 Kotlin + Compose 写得了」——外置插件正是那种插件。
        check("声明式页面能注册并交给宿主渲染") {
            val core = PluginCore(store)
            var tooMany: RegisterResult? = null
            val p = ProbePlugin(manifestOf("test.simple")) { host ->
                host.pages.add(
                    SimplePage(id = "test.simple:page", title = "声明页")
                        .sub("自测")
                        .stat("行数", "2")
                        .button("＋", "点我", actionId = "inc")
                        .onAction { }
                )
                tooMany = host.pages.add(SimplePage(id = "test.other:page", title = "越权"))
            }
            when (val r = core.install(p)) {
                is RegisterResult.Ok -> Unit
                is RegisterResult.Rejected -> return@check "声明式页面没装上：" + r.reason
            }
            val spec = core.page("test.simple:page") ?: return@check "注册后查不到声明式页面"
            if (spec !is SimplePage) return@check "声明式页面被存成了别的类型：" + spec.kind
            if (spec.kind != PageKind.SUB) return@check "声明式页面的 kind 不是 SUB：" + spec.kind
            if (spec.rows.size != 2) return@check "行数不对：" + spec.rows.size
            if (tooMany !is RegisterResult.Rejected) return@check "没带自己前缀的声明式页面被接受了"
            if (core.pagesOf("test.simple").size != 1) return@check "页面归属不对"
            core.uninstall("test.simple")
            if (core.page("test.simple:page") != null) "卸载后声明式页面还留着" else null
        }

        // 30. 页面型外置插件示例包：第 27 项装的是按钮型示例，页面链路（SimplePage -> 宿主渲染）
        //     在真机上没有覆盖过；这条把内置的 page-demo.wplugin 真装一遍，并要求它注册出页面。
        check("页面型外置插件示例包真装一遍") {
            val hook = externalPageSelfTestInstall ?: return@check "没有可用的外置插件安装器（页面示例）"
            hook.invoke()
        }

        return results
    }
}
