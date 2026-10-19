package com.andaxin.core.net

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * 登录口令的加密。
 *
 * 证据：2026-10-19 16:47 本机直发 `in.aiit.edu.cn/zhxy-information/mobile/getToken.do`，
 * 用下面这把公钥、`RSA/PKCS1PADDING`、Base64(NO_WRAP) 加密口令，服务器回了
 * `data.token.access_token`（36 位）—— 也就是说这把公钥和这套填充方式是服务器认的。
 * 之前它只是"旧实现里写着"，现在是我们自己打出去验过的。
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
