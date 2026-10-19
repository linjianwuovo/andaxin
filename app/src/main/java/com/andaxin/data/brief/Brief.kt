package com.andaxin.data.brief

import androidx.compose.runtime.Immutable

/*
 * 一批"列表 / 详情 / 表单"形状的通用契约。
 *
 * 为什么要这一层：没抓包的那十几块（查寝、成绩、学分、劳动、节假日、报修、待办事务、AI 课堂）
 * 界面形状其实是一样的 —— 一行有标题副标题和一个状态，点进去是若干键值对加几段正文，
 * 要填的就是一组字段。先把形状定下来，每块只写一个 source，
 * 他补完抓包只换 source 的实现，界面一行不动。
 *
 * 每个 source 顶上那行【取证】是本仓的规矩：写清字段有没有实样顶着。
 */

enum class Flag { None, Ok, Bad, Warn, Unread }

@Immutable
data class BriefRow(
    val id: String,
    val title: String,
    val sub: String = "",
    val right: String = "",
    val flag: Flag = Flag.None,
)

@Immutable
data class BriefDetail(
    val title: String,
    val fields: List<Pair<String, String>> = emptyList(),
    val paragraphs: List<String> = emptyList(),
    /** 界面上给不给"提交"这类写操作按钮。写操作一律停在最后一步由人按。 */
    val writable: Boolean = false,
    val writeLabel: String = "",
)

@Immutable
sealed class Field {
    abstract val key: String
    abstract val label: String

    data class Text(override val key: String, override val label: String, val hint: String = "") : Field()
    data class Number(override val key: String, override val label: String, val unit: String = "") : Field()
    data class Choice(override val key: String, override val label: String, val options: List<String>) : Field()
    data class Date(override val key: String, override val label: String) : Field()
    data class Person(override val key: String, override val label: String) : Field()
}

@Immutable
data class FormSpec(val title: String, val fields: List<Field>)

interface ListSource {
    suspend fun rows(): Result<List<BriefRow>>
}

interface DetailSource {
    suspend fun detail(id: String): Result<BriefDetail>

    /**
     * 详情里那颗写操作键（`BriefDetail.writable` 为真时才露出来）。
     * 默认不给：没接上报文的一律回 failure，界面就只显示那句原因，绝不静默成功。
     * 真发之前界面要连按两次确认 —— 写操作停在最后一步由人按。
     */
    suspend fun act(id: String): Result<String> =
        Result.failure(Exception("这一屏没有可发的写操作"))
}

interface FormSource {
    suspend fun spec(): Result<FormSpec>

    /** 提交。没接上报文时一律回 failure，不许静默成功。 */
    suspend fun submit(values: Map<String, String>): Result<String>
}
