package com.ioo.watchnote.plugin.external

import java.io.File
import java.util.zip.ZipFile

/**
 * 一个通过结构检查的外置插件包（还没加载、没执行）。
 * dexEntries / assetEntries 都是包内路径；totalUncompressedBytes 是解压后的总大小；
 * entryDigests 是每个文件的摘要（验签用）；signature 是验签结果（Unsigned / Valid / Invalid）。
 */
data class ExternalPluginPackage(
    val manifest: ExternalPluginManifest,
    val dexEntries: List<String>,
    val assetEntries: List<String>,
    val totalUncompressedBytes: Long,
    val entryDigests: List<EntryDigest> = emptyList(),
    val signature: ExternalSignatureResult = ExternalSignatureResult.Unsigned,
)

/** 包结构检查结果：要么可用，要么给出人话原因。 */
sealed interface ExternalPackageResult {
    data class Valid(val pkg: ExternalPluginPackage) : ExternalPackageResult
    data class Invalid(val reason: String) : ExternalPackageResult
}

/**
 * .wplugin 包的结构检查（v2.0，签名从 4.1.14 起）。
 *
 * 为什么先检查再解压：zip 是最容易藏坏东西的地方——路径穿越（../../）、
 * 缺清单、没有代码、塞一堆宿主不认识的文件。这里全部在「打开 zip、读清单、
 * 看条目」这一层挡住，绝不把没验过的条目往磁盘写。
 *
 * 只允许四类文件：
 *   plugin.json      清单（必须有）
 *   plugin.sig       作者签名（可选，Base64 的 DER 签名，≤4 KB）
 *   classes*.dex     插件代码（至少一个）
 *   assets/xxx       插件自带资源（可选）
 * 目录条目允许，但同样要过路径检查。
 *
 * 验签也在这里做：一遍读 zip 顺手算每个文件的 SHA-256，拼出来的清单如果和签名对不上，
 * 直接 Invalid，连解压都不会发生。
 */
object ExternalPackageChecker {

    const val MAX_PACKAGE_BYTES = 4L * 1024 * 1024
    const val MAX_ENTRIES = 128
    const val MAX_TOTAL_UNCOMPRESSED = 16L * 1024 * 1024
    const val MAX_ENTRY_UNCOMPRESSED = 8L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 64L * 1024

    private val dexName = Regex("classes[0-9]*\\.dex")

    fun inspect(file: File): ExternalPackageResult {
        if (!file.isFile) return ExternalPackageResult.Invalid("插件包不存在：" + file.path)
        val length = file.length()
        if (length <= 0L) return ExternalPackageResult.Invalid("插件包是空文件")
        if (length > MAX_PACKAGE_BYTES) {
            return ExternalPackageResult.Invalid("插件包太大（上限 " + (MAX_PACKAGE_BYTES / 1024 / 1024) + " MB）")
        }
        val zip = try {
            ZipFile(file)
        } catch (t: Throwable) {
            return ExternalPackageResult.Invalid("不是合法的 zip：" + (t.message ?: t.javaClass.simpleName))
        }
        zip.use { archive ->
            var entryCount = 0
            var totalBytes = 0L
            var manifestJson: String? = null
            var signatureBase64: String? = null
            val dexEntries = ArrayList<String>()
            val assetEntries = ArrayList<String>()
            val digests = ArrayList<EntryDigest>()
            val seen = HashSet<String>()
            for (entry in archive.entries()) {
                entryCount++
                if (entryCount > MAX_ENTRIES) {
                    return ExternalPackageResult.Invalid("包内条目太多（上限 " + MAX_ENTRIES + " 个）")
                }
                val name = entry.name.replace('\\', '/')
                val nameProblem = checkName(name)
                if (nameProblem != null) return ExternalPackageResult.Invalid(nameProblem)
                if (entry.isDirectory) continue
                if (!seen.add(name)) return ExternalPackageResult.Invalid("包内有重复文件：" + name)
                val size = entry.size
                if (size < 0L) return ExternalPackageResult.Invalid("包内文件缺少大小信息：" + name)
                if (size > MAX_ENTRY_UNCOMPRESSED) {
                    return ExternalPackageResult.Invalid("包内单个文件太大：" + name)
                }
                totalBytes += size
                if (totalBytes > MAX_TOTAL_UNCOMPRESSED) {
                    return ExternalPackageResult.Invalid(
                        "包解压后太大（上限 " + (MAX_TOTAL_UNCOMPRESSED / 1024 / 1024) + " MB）",
                    )
                }
                when {
                    name == PLUGIN_PACKAGE_MANIFEST_NAME -> {
                        if (size > MAX_MANIFEST_BYTES) {
                            return ExternalPackageResult.Invalid("plugin.json 太大（上限 " + (MAX_MANIFEST_BYTES / 1024) + " KB）")
                        }
                        manifestJson = try {
                            archive.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        } catch (t: Throwable) {
                            return ExternalPackageResult.Invalid("读不到 plugin.json：" + (t.message ?: t.javaClass.simpleName))
                        }
                    }
                    name == ExternalPluginSignature.SIGNATURE_ENTRY -> {
                        if (size <= 0L) return ExternalPackageResult.Invalid("plugin.sig 是空文件")
                        if (size > ExternalPluginSignature.MAX_SIGNATURE_BYTES) {
                            return ExternalPackageResult.Invalid(
                                "plugin.sig 太大（上限 " + (ExternalPluginSignature.MAX_SIGNATURE_BYTES / 1024) + " KB）",
                            )
                        }
                        signatureBase64 = try {
                            archive.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                        } catch (t: Throwable) {
                            return ExternalPackageResult.Invalid("读不到 plugin.sig：" + (t.message ?: t.javaClass.simpleName))
                        }
                    }
                    dexName.matches(name) -> {
                        if (size == 0L) return ExternalPackageResult.Invalid("包内代码文件是空的：" + name)
                        dexEntries.add(name)
                    }
                    name.startsWith("assets/") -> assetEntries.add(name)
                    else -> return ExternalPackageResult.Invalid(
                        "包里有不认识的文件：" + name + "（只允许 plugin.json、plugin.sig、classes*.dex、assets/…）",
                    )
                }
                // 每读一个文件顺手记摘要；签名文件自己不参与（它就是签名）。
                if (name != ExternalPluginSignature.SIGNATURE_ENTRY) {
                    val digest = try {
                        archive.getInputStream(entry).use { input -> ExternalPluginSignature.sha256Hex(input) }
                    } catch (t: Throwable) {
                        return ExternalPackageResult.Invalid("读不到包内文件：" + name)
                    }
                    digests.add(EntryDigest(name = name, size = size, sha256 = digest))
                }
            }
            if (manifestJson == null) return ExternalPackageResult.Invalid("包内缺少 plugin.json")
            if (dexEntries.isEmpty()) return ExternalPackageResult.Invalid("包内缺少 classes.dex（没有代码）")
            val parsed = ExternalPluginManifestParser.parse(manifestJson)
            if (parsed is ExternalManifestResult.Invalid) {
                return ExternalPackageResult.Invalid("plugin.json 不合法：" + parsed.reason)
            }
            val valid = parsed as ExternalManifestResult.Valid
            val external = valid.parsed

            // 验签：没声明签名就是 Unsigned（合法）；声明了就必须验过，否则不进安装流程。
            val signature = ExternalPluginSignature.verify(
                entries = digests,
                algorithm = external.signingAlgorithm,
                publicKeyB64 = external.publicKey,
                signatureB64 = signatureBase64,
            )
            if (signature is ExternalSignatureResult.Invalid) {
                return ExternalPackageResult.Invalid("签名校验失败：" + signature.reason)
            }

            return ExternalPackageResult.Valid(
                ExternalPluginPackage(
                    manifest = external,
                    dexEntries = dexEntries.sorted(),
                    assetEntries = assetEntries.sorted(),
                    totalUncompressedBytes = totalBytes,
                    entryDigests = digests.sortedBy { it.name },
                    signature = signature,
                ),
            )
        }
    }

    /** 路径检查：不许绝对路径、不许 .. 跳出目录、不许过长的名字。 */
    private fun checkName(name: String): String? {
        if (name.isBlank()) return "包内有空文件名的条目"
        if (name.length > 200) return "包内文件名太长：" + name.take(40) + "…"
        if (name.startsWith("/")) return "包内文件用了绝对路径：" + name
        if (name.contains(":")) return "包内文件名不合法（含冒号）：" + name
        for (segment in name.split('/')) {
            if (segment == "..") return "包内文件想跳出目录：" + name
        }
        return null
    }
}
