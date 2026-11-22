package com.ioo.watchnote.plugin.external

import java.io.InputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** 包内一个文件的摘要：路径、解压后字节数、内容 SHA-256（小写十六进制）。 */
data class EntryDigest(val name: String, val size: Long, val sha256: String)

/** 验签结果。Unsigned 是合法状态（能装，只是标成「未签名」）。 */
sealed interface ExternalSignatureResult {
    object Unsigned : ExternalSignatureResult
    data class Valid(val keyFingerprint: String) : ExternalSignatureResult
    data class Invalid(val reason: String) : ExternalSignatureResult
}

/**
 * 外置插件作者签名（v2.2，4.1.14 起）。
 *
 * 为什么签「清单」而不是整包字节：zip 的压缩方式、时间戳、条目顺序都不稳定，
 * 同一份代码打两次包字节就不一样，直接签整包会把正常重打包判成篡改。
 * 这里签的是「每个文件的 SHA-256 + 字节数 + 路径」，按路径排序拼成一段文本，
 * 用 ECDSA P-256 / SHA-256 签名；任何一个文件被改，文本就变，签名对不上。
 *
 * 文本格式（UTF-8，行尾 \n）：
 *   WRISTNOTE-PLUGIN-SIGNATURE-V1
 *   <sha256小写hex> <字节数> <包内路径>
 *   ...
 * 条目按路径的 UTF-8 字节序升序；plugin.sig 自己不参与。
 *
 * 公钥放在 plugin.json 的 publicKey（Base64 的 X.509 SubjectPublicKeyInfo），
 * 签名放包里的 plugin.sig（Base64 的 DER 签名）。首次安装记下公钥指纹，
 * 以后同一个 id 换钥匙就拒绝——这是这套东西唯一能提供的东西：
 * 「同一个 id，还是不是同一个作者」，不是「这个包绝对安全」。
 */
object ExternalPluginSignature {

    const val SIGNATURE_ENTRY = "plugin.sig"
    const val ALGORITHM = "SHA256withECDSA"
    const val MAX_SIGNATURE_BYTES = 4096L
    const val MAX_PUBLIC_KEY_BYTES = 1024
    const val FINGERPRINT_CHARS = 16

    private const val DOMAIN = "WRISTNOTE-PLUGIN-SIGNATURE-V1\n"

    /** 把条目摘要拼成待签名的文本。顺序是按路径 UTF-8 字节序，跨平台稳定。 */
    fun payload(entries: List<EntryDigest>): ByteArray {
        val sorted = entries.sortedWith { a, b -> compareUtf8(a.name, b.name) }
        val sb = StringBuilder(DOMAIN)
        for (e in sorted) {
            sb.append(e.sha256.lowercase())
                .append(' ')
                .append(e.size)
                .append(' ')
                .append(e.name)
                .append('\n')
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun sha256Hex(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        return hex(digest.digest())
    }

    /** 公钥指纹：对公钥字节做 SHA-256，取前 16 个十六进制字符（大写），给人看。 */
    fun fingerprintOfPublicKey(publicKeyB64: String): String? =
        try {
            hex(MessageDigest.getInstance("SHA-256").digest(publicKeyBytes(publicKeyB64)))
                .take(FINGERPRINT_CHARS)
                .uppercase()
        } catch (t: Throwable) {
            null
        }

    fun publicKeyBytes(publicKeyB64: String): ByteArray = Base64.getDecoder().decode(publicKeyB64.trim())

    /**
     * 验签。三种结果：Unsigned（没说签名，合法）、Valid（带指纹）、Invalid（说签了但验不过）。
     * 注意「清单写了 publicKey 却没 plugin.sig」「有 plugin.sig 却没 publicKey」都算 Invalid，
     * 不让「半签名」蒙混过去。
     */
    fun verify(
        entries: List<EntryDigest>,
        algorithm: String?,
        publicKeyB64: String?,
        signatureB64: String?,
    ): ExternalSignatureResult {
        val key = publicKeyB64?.takeIf { it.isNotBlank() }
        val sig = signatureB64?.takeIf { it.isNotBlank() }
        if (key == null) {
            return if (sig == null) ExternalSignatureResult.Unsigned
            else ExternalSignatureResult.Invalid("包里带 plugin.sig，但清单没写 publicKey，没法验签")
        }
        if (algorithm != null && algorithm != ALGORITHM) {
            return ExternalSignatureResult.Invalid("不支持的签名算法：" + algorithm + "（只认 " + ALGORITHM + "）")
        }
        if (sig == null) {
            return ExternalSignatureResult.Invalid("清单写了 publicKey，但包里没有 plugin.sig")
        }
        val keyBytes = try {
            publicKeyBytes(key)
        } catch (t: Throwable) {
            return ExternalSignatureResult.Invalid("publicKey 不是合法 Base64")
        }
        if (keyBytes.isEmpty() || keyBytes.size > MAX_PUBLIC_KEY_BYTES) {
            return ExternalSignatureResult.Invalid("publicKey 长度不合法（" + keyBytes.size + " 字节）")
        }
        val sigBytes = try {
            Base64.getDecoder().decode(sig)
        } catch (t: Throwable) {
            return ExternalSignatureResult.Invalid("plugin.sig 不是合法 Base64")
        }
        if (sigBytes.isEmpty() || sigBytes.size > MAX_SIGNATURE_BYTES) {
            return ExternalSignatureResult.Invalid("签名长度不合法（" + sigBytes.size + " 字节）")
        }
        return try {
            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
            val verifier = Signature.getInstance(ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(payload(entries))
            if (!verifier.verify(sigBytes)) {
                ExternalSignatureResult.Invalid("签名对不上（包被改过，或者不是这把钥匙签的）")
            } else {
                ExternalSignatureResult.Valid(
                    hex(MessageDigest.getInstance("SHA-256").digest(keyBytes))
                        .take(FINGERPRINT_CHARS)
                        .uppercase(),
                )
            }
        } catch (t: Throwable) {
            ExternalSignatureResult.Invalid("验签失败：" + (t.message ?: t.javaClass.simpleName))
        }
    }

    /** 造签名。只给自测和作者工具用，宿主不签任何东西。 */
    fun signForTest(entries: List<EntryDigest>, privateKey: PrivateKey): String {
        val signer = Signature.getInstance(ALGORITHM)
        signer.initSign(privateKey)
        signer.update(payload(entries))
        return Base64.getEncoder().encodeToString(signer.sign())
    }

    private fun compareUtf8(a: String, b: String): Int {
        val x = a.toByteArray(Charsets.UTF_8)
        val y = b.toByteArray(Charsets.UTF_8)
        val n = minOf(x.size, y.size)
        for (i in 0 until n) {
            val dx = x[i].toInt() and 0xff
            val dy = y[i].toInt() and 0xff
            if (dx != dy) return dx - dy
        }
        return x.size - y.size
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { String.format("%02x", it) }
}
