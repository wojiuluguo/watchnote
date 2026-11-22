package com.ioo.watchnote.plugin.external

/**
 * 外置插件的升级策略（纯函数，不碰文件、不碰 Context，便于自测）。
 *
 * 规则只有一条：同一个 id 再装时，新包版本不能比已装的低；同版本算重装，允许。
 * 为什么拒绝降级：插件数据是按 id 存的，老版本很可能读不懂新版本写下的数据，
 * 降级后轻则显示空白、重则崩溃，不如当场拒绝并说清楚。
 */
object ExternalInstallPolicy {

    /** 返回 null 表示可以装；否则返回人话原因。installedVersion 为 null 表示第一次装。 */
    fun checkVersion(installedVersion: Int?, incomingVersion: Int): String? {
        if (installedVersion == null) return null
        if (incomingVersion < installedVersion) {
            return "已安装 v" + installedVersion + "，这个包是 v" + incomingVersion + "，不允许降级"
        }
        return null
    }

    /**
     * 作者签名策略（4.1.14 起）：已装版本有签名指纹，新包就必须是同一把钥匙。
     * 换钥匙、或者新包根本没签名，都拒绝——这是「同一个 id 还是不是同一个作者」的底线。
     * 反过来：已装没签名，新包带签名，允许（等于升级到签名版，装上后记下指纹）。
     */
    fun checkSigning(installedFingerprint: String?, incomingFingerprint: String?): String? {
        val installed = installedFingerprint?.takeIf { it.isNotBlank() } ?: return null
        if (incomingFingerprint.isNullOrBlank()) {
            return "已装的版本由作者签名（指纹 " + installed + "），这个包没签名，拒绝安装"
        }
        if (!incomingFingerprint.equals(installed, ignoreCase = true)) {
            return "作者签名对不上（已装 " + installed + "，新包 " + incomingFingerprint + "），可能是被换过的包，拒绝安装"
        }
        return null
    }

    /** 签名状态的一句话说明，给确认弹窗和详情页用。 */
    fun signingLabel(fingerprint: String?): String =
        if (fingerprint.isNullOrBlank()) "未签名" else "已签名 · " + fingerprint

    /** 安装确认弹窗上显示这次是首装、升级还是重装。 */
    fun label(installedVersion: Int?, incomingVersion: Int): String = when {
        installedVersion == null -> "首次安装"
        incomingVersion > installedVersion -> "升级 v" + installedVersion + " → v" + incomingVersion
        else -> "重装 v" + incomingVersion
    }
}
