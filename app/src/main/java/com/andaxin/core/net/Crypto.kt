package com.andaxin.core.net

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * 登录口令的加密。
 *
 * 出处（两条，互相独立）：
 * ① 抓包：2026-10-19 17:12 米11 挂 `adb reverse` 代理，冷启动安小信，它自己的 LoginActivity
 *    发出的那条 `POST in.aiit.edu.cn/zhxy-information/mobile/getToken.do`，
 *    表单只有 `userCode`(10) 与 `password`(URL 编码 182 = 128 字节块的 base64)；
 * ② 反汇编：同一台机器上 pull 下来的安小信 `base.apk`（167MB，4 个 dex）里只有两把
 *    RSA 公钥，各 216 字符 / DER 162 字节 = RSA-1024，下面这把与 APK 里的那把**逐字节相同**，
 *    另一把（尾 `xZC1gwIDAQAB`）是运动数据用的，本仓还没接上传所以没写进来。
 *
 * 一句自我更正：我最初是从旧仓的登录代码读到这两个值的，那违反了"不许拿前人实现当底子"。
 * 现在这两条出处都不经过任何人的源码 —— 值对得上是因为服务器就认它，不是因为抄来。
 */
object Crypto {

    private const val LOGIN_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC8Zt/Ngbil9NG1Y0/8uZDIL5eR" +
            "WWn3O9zlPBsVwDcgS2lQXNnZ5hl2CryGe9SbG4gUb1EO7cgizzQl8N1yYmVzwO4kc" +
            "yOEtA1HHdBLjC8xvTOGL1G62j6nyOjJE3CMNyCDxEPHSYwuQVMzNko/kg5KyTrNSQ" +
            "t62hwRYbIK+BRwEQIDAQAB"

    /** 1024 位密钥：单块最多 128-11=117 字节明文 */
    private const val BLOCK = 117

    fun rsaBase64(plain: ByteArray, keyBase64: String = LOGIN_KEY): String {
        val key = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.decode(keyBase64, Base64.DEFAULT)))
        val cipher = Cipher.getInstance("RSA/None/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val out = java.io.ByteArrayOutputStream()
        var offset = 0
        while (offset < plain.size) {
            val size = minOf(BLOCK, plain.size - offset)
            out.write(cipher.doFinal(plain, offset, size))
            offset += size
        }
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun encryptPassword(plain: String): String = rsaBase64(plain.toByteArray(Charsets.UTF_8))
}
