package com.andaxin.core.net

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFErrorCopyDescription
import platform.CoreFoundation.CFErrorGetCode
import platform.CoreFoundation.CFErrorRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecKeyCreateEncryptedData
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSAEncryptionPKCS1

/**
 * iOS：Security 框架的 RSA/PKCS1。keyBase64 解出来就是 X509 SubjectPublicKeyInfo 的 DER，
 * SecKeyCreateWithData 直接吃这个格式；117 字节分块与 JVM 侧口径一致。
 *
 * 0fa77c5 那版在这儿漏了一个键：属性字典只塞了 kSecAttrKeyType，没塞 kSecAttrKeyClass。
 * Apple 的契约是这两个都必须给（位数由 X.509 DER 自己带），少一个 SecKeyCreateWithData 就回
 * NULL，于是点"登录"当场抛 IllegalStateException，一路没人接、abort() 掉整个进程 ——
 * 真机 AndaXin-2026-10-26-020009.ips 的栈顶就是这个函数。
 * 顺带两处：CFDictionaryCreateMutable 的 callbacks 上一版传的是 null，这里换成 kCFType 那一对；
 * 报错不再写"见控制台"，把 CFError 的 code 和描述带进异常，下次不用拿手机复现。
 */
@OptIn(ExperimentalEncodingApi::class, ExperimentalForeignApi::class)
actual fun rsaEncryptPkcs1Base64(plain: ByteArray, keyBase64: String): String {
    // 容一手：公钥哪天从 PEM/配置里带换行进来了，Base64.Default 是会直接抛的
    val keyDer = Base64.Default.decode(keyBase64.replace("\\s".toRegex(), ""))

    val key: SecKeyRef = memScoped {
        val derData: CFDataRef? = keyDer.usePinned {
            CFDataCreate(null, it.addressOf(0).reinterpret(), keyDer.size.toLong())
        } ?: throw IllegalStateException("公钥 DER 造不出来（长度 ${keyDer.size}）")
        val attrs = CFDictionaryCreateMutable(
            null,
            3L,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: throw IllegalStateException("属性字典造不出来")
        CFDictionarySetValue(attrs, kSecAttrKeyType, kSecAttrKeyTypeRSA)
        CFDictionarySetValue(attrs, kSecAttrKeyClass, kSecAttrKeyClassPublic)
        val keyErr = alloc<CFErrorRefVar>()
        SecKeyCreateWithData(derData, attrs, keyErr.ptr)
            ?: throw IllegalStateException("公钥解析失败：${describe(keyErr.value)}")
    }

    val out = ArrayList<Byte>(plain.size / 117 * 128 + 128)
    var offset = 0
    while (offset < plain.size) {
        val size = minOf(117, plain.size - offset)
        val block = plain.copyOfRange(offset, offset + size)
        memScoped {
            val err = alloc<CFErrorRefVar>()
            val blockData: CFDataRef? = block.usePinned {
                CFDataCreate(null, it.addressOf(0).reinterpret(), block.size.toLong())
            }
            val encrypted = SecKeyCreateEncryptedData(
                key,
                kSecKeyAlgorithmRSAEncryptionPKCS1,
                blockData,
                err.ptr,
            ) ?: throw IllegalStateException(
                "RSA 加密失败：${describe(err.value)}（第 ${offset / 117 + 1} 块 $size 字节）",
            )
            val len = CFDataGetLength(encrypted)
            CFDataGetBytePtr(encrypted)?.readBytes(len.toInt())?.let { out.addAll(it.toList()) }
        }
        offset += size
    }
    return Base64.Default.encode(out.toByteArray())
}

/** CFError → "code=-4 …"。code 就够定位了，后面那句描述是白送的。 */
@OptIn(ExperimentalForeignApi::class)
private fun MemScope.describe(err: CFErrorRef?): String {
    if (err == null) return "无 error 对象"
    val code = CFErrorGetCode(err)
    val desc = CFErrorCopyDescription(err) ?: return "code=$code（没给描述）"
    val cap = (CFStringGetLength(desc) * 4L + 1L).toInt()
    val buf = allocArray<ByteVar>(cap)
    return if (CFStringGetCString(desc, buf, cap.toLong(), kCFStringEncodingUTF8)) {
        "code=$code ${buf.toKString()}"
    } else {
        "code=$code"
    }
}
