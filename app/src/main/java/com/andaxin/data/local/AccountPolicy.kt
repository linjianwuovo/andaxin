package com.andaxin.data.local

import java.security.MessageDigest

/**
 * 本机对登录账号的限制名单。
 *
 * 只做一件事：名单里的账号**不发请求**，登录页上回一句中性说明。拒绝发生在本地，
 * 所以那句话里不出现"学校"两个字 —— 那不是校方的答复。
 *
 * 名单里存的是账号的指纹（SHA-256 十六进制前 32 位），不是学号本身：学号不进源码、
 * 不进 dex，`strings` 也扫不出来。要加一条就把下面那行的值换成新账号的指纹：
 *
 *     python -c "import hashlib;print(hashlib.sha256(b'<账号>').hexdigest()[:32])"
 *
 * 要去掉一条就删掉那一行；要改屏幕上那句话，改 [NOTICE]。
 */
object AccountPolicy {

    /** 本机不允许登入的账号指纹。 */
    private val blocked = setOf("a8080b1c75a1db6d75d3502132342106")

    /** 被限制时屏幕上显示的那句话。 */
    const val NOTICE = "该账号在本机不可用"

    fun isBlocked(userCode: String): Boolean = fingerprint(userCode) in blocked

    private fun fingerprint(userCode: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(userCode.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
}
