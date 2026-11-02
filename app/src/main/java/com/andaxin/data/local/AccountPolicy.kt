package com.andaxin.data.local

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 本机对登录账号的限制名单。
 *
 * 只做一件事：名单里的账号**不发请求**，登录页上回一句中性说明。拒绝发生在本地，
 * 所以那句话里不出现"学校"两个字 —— 那不是校方的答复。
 *
 * 名单里既不存学号，也不存它的裸哈希：每条是 `HMAC-SHA256(秘钥, 账号)` 的前 16 字节，
 * 而秘钥本身也不是字面量 —— 由 [seed] 那 8 个 32 位整数乘以 [MIX]、取无符号位移后
 * SHA-256 得到（见 [secret]）。静态翻 dex：拿不到账号、拿不到整齐的十六进制串、
 * 也拿不到秘钥，要还原必须把这段推导照抄一遍。
 *
 * 说清天花板：学号只有 100 亿种，拿到包的人真去枚举的话几小时量级仍可能撞出来 ——
 * 任何"本地自己说了算"的拦截都躲不过这一条，这里堵掉的是"翻包一眼看出来"那条路。
 *
 * 要加一条（把 <账号> 换掉，值直接填进 [blocked]）：
 *
 *     seed=[0x2E4C71A5,0x8B13D6F0,0x47A9C32E,0xD05B178C,0x63F2A98D,0xB71E4506,0x1C8D53AF,0xE96B0C42]
 *     MIX=0x9E3779B9
 *     b=bytes((((seed[i//4]*MIX)&0xFFFFFFFF)>>((i%4)*8))&0xFF for i in range(32))
 *     k=hashlib.sha256(b).digest()
 *     d=hmac.new(k,b"<账号>",hashlib.sha256).digest()[:16]
 *     print(", ".join("0x%08X.toInt()"%int.from_bytes(d[i*4:i*4+4],"big") for i in range(4)))
 *
 * 要改屏幕上那句话，改 [NOTICE]。
 */
object AccountPolicy {

    /** 本机不允许登入的账号标签，每条 4 个 32 位整数（HMAC 前 16 字节，大端）。 */
    private val blocked = listOf(
        intArrayOf(0x306562FD.toInt(), 0xD1933AC4.toInt(), 0x29CEA280.toInt(), 0x1C75F8A0.toInt()),
    )

    /** 被限制时屏幕上显示的那句话。 */
    const val NOTICE = "该账号在本机不可用"

    private const val MIX = 0x9E3779B9.toInt()

    private val seed = intArrayOf(
        0x2E4C71A5, 0x8B13D6F0.toInt(), 0x47A9C32E, 0xD05B178C.toInt(),
        0x63F2A98D, 0xB71E4506.toInt(), 0x1C8D53AF, 0xE96B0C42.toInt(),
    )

    /** 秘钥：种子乘 [MIX] 后按无符号位移摊成字节，再 SHA-256。不是字面量，翻包看不到。 */
    private val secret: ByteArray = MessageDigest.getInstance("SHA-256").digest(
        ByteArray(seed.size * 4) { i ->
            val v = seed[i / 4] * MIX
            (v ushr ((i % 4) * 8)).toByte()
        },
    )

    fun isBlocked(userCode: String): Boolean = blocked.any { it.contentEquals(tagOf(userCode)) }

    private fun tagOf(userCode: String): IntArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val d = mac.doFinal(userCode.trim().toByteArray(Charsets.UTF_8))
        return IntArray(4) { i ->
            ((d[i * 4].toInt() and 0xFF) shl 24) or
                ((d[i * 4 + 1].toInt() and 0xFF) shl 16) or
                ((d[i * 4 + 2].toInt() and 0xFF) shl 8) or
                (d[i * 4 + 3].toInt() and 0xFF)
        }
    }
}
