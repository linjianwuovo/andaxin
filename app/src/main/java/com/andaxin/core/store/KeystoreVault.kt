package com.andaxin.core.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 「记住密码」的存放处：口令用 Android Keystore 里一把**不可导出**的 AES-256-GCM 密钥加密，
 * 密文（12 字节 IV + GCM 密文+tag）base64 之后才落 DataStore。
 *
 * 为什么要绕这一圈而不是直接存字符串：
 * 1. 存明文口令等于把校园账号的钥匙写在 `/data/data` 底下随便一个能读文件的地方；
 *    一旦 root/备份/取证工具碰到，泄露的是**账号本身**，不是某一个 token。
 * 2. 只有 Keystore 的密钥是硬件后端的机型上，密文拷到别的机器解不开 —— 所以这个文件
 *    跟着备份跑出去也没用（本仓 `allowBackup="false"`，本来就不同步）。
 *
 * 为什么需要记住口令这件事本身：**校方那条 `mobile/refresh.do` 服务端是死的**
 * （10-19 现打 404，六种打法全一样，见 `AuthFeed.refresh` 那段），`refresh_token` 换不出新 token。
 * 所以"续期"只有一条路 = **拿口令重新登一次**。没有记住口令，App 每 8 小时、
 * 以及每次被别的客户端顶号，都只能把人请回登录页重打一遍。
 *
 * 一个副作用必须写明白：校方**一个账号只留一个活会话**，所以"被顶号后自动重登"
 * 会把对方（比如安小信）挤下线。界面上那句开关说明就是这么写的，不藏。
 *
 * 失败一律吞成"没有记住"：Keystore 不可用、密钥被系统清掉（改指纹/恢复出厂会）、
 * 密文损坏 —— 这些都让用户重新输一次口令就好，绝不让登录屏打不开。
 */
class KeystoreVault(context: Context) {

    private val appContext = context.applicationContext

    /** 加密一条口令。失败回 null，调用方按"没记住"处理。 */
    fun seal(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }.getOrNull()

    /** 解出口令。密文坏了 / 密钥没了都回 null（并且调用方该把那份密文清掉）。 */
    fun open(blob: String): String? = runCatching {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        if (raw.size <= IV_LEN) return null
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(
            Cipher.DECRYPT_MODE, key(),
            GCMParameterSpec(128, raw.copyOfRange(0, IV_LEN)),
        )
        String(cipher.doFinal(raw, IV_LEN, raw.size - IV_LEN), Charsets.UTF_8)
    }.getOrNull()

    /** 口令能不能解出来 —— 设置那一屏用它决定要不要提示"重新输一次"。 */
    fun usable(blob: String): Boolean = !open(blob).isNullOrBlank()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 不绑指纹/锁屏：绑了每次重登都要生物识别，这台机器上是自找麻烦
                .setUserAuthenticationRequired(false)
                .build(),
        )
        // Keystore 里的对称密钥本来就**没有导出接口**（`KeyStore` 只给 `getEntry`，
        // 对 Keystore 条目返回的是句柄而不是密钥字节），所以这里不需要额外声明"不可导出"，
        // 也没有 `setNonExportable()` 这个方法可写 —— 别为了好看编一个不存在的 API。
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "com.andaxin.prefs.vault"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
