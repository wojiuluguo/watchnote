package com.ioo.watchnote.plugin.external

import com.ioo.watchnote.plugin.Permission
import com.ioo.watchnote.plugin.PluginApi
import com.ioo.watchnote.plugin.PluginDependency
import com.ioo.watchnote.plugin.PluginKind
import com.ioo.watchnote.plugin.PluginManifest
import org.json.JSONObject
import java.io.File

/** 外置插件包（.wplugin）里 plugin.json 的契约版本。加载器还没做，契约先钉死。 */
const val EXTERNAL_SCHEMA_VERSION = 1

/** 一个 .wplugin（其实就是 zip）里清单文件的名字。 */
const val PLUGIN_PACKAGE_MANIFEST_NAME = "plugin.json"

/** 外置插件包的后缀名。 */
const val PLUGIN_PACKAGE_EXTENSION = ".wplugin"

/** 内置插件专用前缀，外置插件不许占。 */
private const val RESERVED_ID_PREFIX = "com.ioo.watchnote"

/** 解析通过的清单：宿主需要的身份证 + 入口类 + 可选的作者信息。 */
data class ExternalPluginManifest(
    val manifest: PluginManifest,
    val entryClass: String,
    /** 普通插件还是前置模块（0.3 起）。 */
    val kind: PluginKind = PluginKind.APP,
    /** 前置模块对外提供的符号（完整类名）。 */
    val provides: List<String> = emptyList(),
    /** 本插件依赖的前置模块。 */
    val requires: List<PluginDependency> = emptyList(),
    val schemaVersion: Int = EXTERNAL_SCHEMA_VERSION,
    val author: String? = null,
    val description: String? = null,
    val signingAlgorithm: String = ExternalPluginSignature.ALGORITHM,
    val publicKey: String? = null,
)

/** 校验结果：要么完整可用，要么给出人话原因。 */
sealed interface ExternalManifestResult {
    data class Valid(val parsed: ExternalPluginManifest) : ExternalManifestResult
    data class Invalid(val reason: String) : ExternalManifestResult
}

/**
 * plugin.json 的解析与校验。纯函数、不加载 dex、不执行插件代码。
 * 规则：反向域名 id（禁 com.ioo.watchnote 前缀）、name/version、minApi 不超宿主、
 * permissions 只许已知枚举、entryClass 必须是完整类名。
 */
object ExternalPluginManifestParser {

    /** 一个前置模块最多对外提供多少个符号、一个插件最多依赖多少个前置模块。 */
    private const val MAX_PROVIDES = 16
    private const val MAX_REQUIRES = 8

    private val idPattern = Regex("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")
    private val classNamePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")

    /** 解析一条依赖：com.example.uikit 或 com.example.uikit>=2。 */
    private fun parseDependency(raw: String): PluginDependency? {
        if (raw.isEmpty() || raw.length > 96) return null
        val parts = raw.split(">=", limit = 2)
        val id = parts[0].trim()
        if (!idPattern.matches(id)) return null
        val minVersion = if (parts.size == 2) {
            parts[1].trim().toIntOrNull() ?: return null
        } else {
            1
        }
        if (minVersion < 1) return null
        return PluginDependency(id, minVersion)
    }

    fun parseFile(file: File): ExternalManifestResult =
        try {
            parse(file.readText(Charsets.UTF_8))
        } catch (t: Throwable) {
            ExternalManifestResult.Invalid("读不到插件清单：" + (t.message ?: t.javaClass.simpleName))
        }

    fun parse(json: String): ExternalManifestResult {
        val root = try {
            JSONObject(json)
        } catch (t: Throwable) {
            return ExternalManifestResult.Invalid("清单不是合法 JSON：" + (t.message ?: "格式错误"))
        }

        val schema = root.optInt("schemaVersion", 0)
        if (schema != EXTERNAL_SCHEMA_VERSION) {
            return ExternalManifestResult.Invalid(
                "不支持的清单版本 " + schema + "，本宿主只认 " + EXTERNAL_SCHEMA_VERSION,
            )
        }

        val id = root.optString("id", "").trim()
        if (!idPattern.matches(id)) {
            return ExternalManifestResult.Invalid("id 必须是反向域名（小写字母/数字/下划线，至少两段），实际：" + id)
        }
        if (id.length > 64) return ExternalManifestResult.Invalid("id 太长（上限 64 字符）")
        if (id == RESERVED_ID_PREFIX || id.startsWith(RESERVED_ID_PREFIX + ".")) {
            return ExternalManifestResult.Invalid("id 不能用保留前缀 " + RESERVED_ID_PREFIX + "（那是内置插件的）")
        }

        val name = root.optString("name", "").trim()
        if (name.isEmpty()) return ExternalManifestResult.Invalid("name 不能为空")
        if (name.length > 24) return ExternalManifestResult.Invalid("name 太长（上限 24 字符）")

        val version = root.optInt("version", 0)
        if (version < 1) return ExternalManifestResult.Invalid("version 必须是 >= 1 的整数")

        val minApi = root.optInt("minApi", PluginApi.API_LEVEL)
        if (minApi < 1) return ExternalManifestResult.Invalid("minApi 必须 >= 1")
        if (minApi > PluginApi.API_LEVEL) {
            return ExternalManifestResult.Invalid(
                "需要宿主 API " + minApi + "，当前宿主只有 " + PluginApi.API_LEVEL,
            )
        }

        val entryClass = root.optString("entryClass", "").trim()
        if (!classNamePattern.matches(entryClass)) {
            return ExternalManifestResult.Invalid("entryClass 必须是完整类名（包名 + 类名），实际：" + entryClass)
        }

        val permissions = LinkedHashSet<Permission>()
        if (root.has("permissions") && !root.isNull("permissions")) {
            val array = root.optJSONArray("permissions")
                ?: return ExternalManifestResult.Invalid("permissions 必须是数组")
            for (i in 0 until array.length()) {
                val raw = array.optString(i, "").trim()
                val perm = Permission.values().firstOrNull { it.name == raw }
                if (perm == null) {
                    return ExternalManifestResult.Invalid(
                        "未知权限：" + raw + "（可用：" + Permission.values().joinToString { it.name } + "）",
                    )
                }
                permissions.add(perm)
            }
        }

        val author = root.optString("author", "").trim().takeIf { it.isNotEmpty() }
        if (author != null && author.length > 40) return ExternalManifestResult.Invalid("author 太长（上限 40 字符）")

        val description = root.optString("description", "").trim().takeIf { it.isNotEmpty() }
        if (description != null && description.length > 200) {
            return ExternalManifestResult.Invalid("description 太长（上限 200 字符）")
        }

        // 0.3 起：插件类型、前置模块提供的符号、依赖关系。
        val kindRaw = root.optString("kind", "app").trim().lowercase()
        val kind = when (kindRaw) {
            "app" -> PluginKind.APP
            "lib" -> PluginKind.LIB
            else -> return ExternalManifestResult.Invalid("kind 只能是 app 或 lib，实际：" + kindRaw)
        }

        val provides = ArrayList<String>()
        if (root.has("provides") && !root.isNull("provides")) {
            val array = root.optJSONArray("provides")
                ?: return ExternalManifestResult.Invalid("provides 必须是数组")
            if (array.length() > MAX_PROVIDES) {
                return ExternalManifestResult.Invalid("provides 太多（上限 " + MAX_PROVIDES + " 个符号）")
            }
            for (i in 0 until array.length()) {
                val symbol = array.optString(i, "").trim()
                if (!classNamePattern.matches(symbol)) {
                    return ExternalManifestResult.Invalid("provides 里的符号必须是完整类名，实际：" + symbol)
                }
                if (symbol.length > 128) return ExternalManifestResult.Invalid("provides 里的符号太长（上限 128 字符）")
                if (!provides.contains(symbol)) provides.add(symbol)
            }
        }
        if (kind == PluginKind.APP && provides.isNotEmpty()) {
            return ExternalManifestResult.Invalid("只有前置模块（kind=lib）才能写 provides")
        }

        val requires = ArrayList<PluginDependency>()
        if (root.has("requires") && !root.isNull("requires")) {
            val array = root.optJSONArray("requires")
                ?: return ExternalManifestResult.Invalid("requires 必须是数组")
            if (array.length() > MAX_REQUIRES) {
                return ExternalManifestResult.Invalid("requires 太多（上限 " + MAX_REQUIRES + " 个）")
            }
            for (i in 0 until array.length()) {
                val raw = array.optString(i, "").trim()
                val parsed = parseDependency(raw)
                    ?: return ExternalManifestResult.Invalid("requires 写法不对：" + raw + "（应像 com.example.uikit 或 com.example.uikit>=2）")
                if (!requires.any { it.id == parsed.id }) requires.add(parsed)
            }
        }

        // 作者签名（4.1.14 起，可选）：publicKey 是 Base64 的 X.509 SPKI 公钥；
        // 只给了算法没给公钥、公钥不是合法 Base64、算法不是 SHA256withECDSA，都当场拒绝。
        val signingAlgorithm = root.optString("signingAlgorithm", "").trim().takeIf { it.isNotEmpty() }
        val publicKey = root.optString("publicKey", "").trim().takeIf { it.isNotEmpty() }
        if (publicKey != null) {
            if (publicKey.length > 2048) return ExternalManifestResult.Invalid("publicKey 太长（上限 2048 字符）")
            val keyBytes = try {
                ExternalPluginSignature.publicKeyBytes(publicKey)
            } catch (t: Throwable) {
                return ExternalManifestResult.Invalid("publicKey 不是合法 Base64")
            }
            if (keyBytes.isEmpty() || keyBytes.size > ExternalPluginSignature.MAX_PUBLIC_KEY_BYTES) {
                return ExternalManifestResult.Invalid("publicKey 长度不合法（" + keyBytes.size + " 字节）")
            }
            if (signingAlgorithm != null && signingAlgorithm != ExternalPluginSignature.ALGORITHM) {
                return ExternalManifestResult.Invalid(
                    "不支持的签名算法：" + signingAlgorithm + "（只认 " + ExternalPluginSignature.ALGORITHM + "）",
                )
            }
        } else if (signingAlgorithm != null) {
            return ExternalManifestResult.Invalid("写了 signingAlgorithm 却没给 publicKey，签名信息不完整")
        }

        return ExternalManifestResult.Valid(
            ExternalPluginManifest(
                manifest = PluginManifest(
                    id = id,
                    name = name,
                    version = version,
                    minApi = minApi,
                    permissions = permissions,
                    kind = kind,
                    provides = provides,
                    requires = requires,
                ),
                entryClass = entryClass,
                kind = kind,
                provides = provides,
                requires = requires,
                schemaVersion = schema,
                author = author,
                description = description,
                signingAlgorithm = signingAlgorithm ?: ExternalPluginSignature.ALGORITHM,
                publicKey = publicKey,
            ),
        )
    }
}
