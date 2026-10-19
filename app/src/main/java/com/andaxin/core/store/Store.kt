package com.andaxin.core.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// 全应用只有这一份首选项文件。
// 旧那套开了 13 个 DataStore 实例，每个都是一份文件加一条协程，全在启动期做磁盘 IO；
// 这里合成一份，键名带业务前缀（auth./theme./msg.）就够分了。
private val Context.preferences: DataStore<Preferences> by preferencesDataStore(name = "andaxin")

/** 设置与会话的本机入口：读用 Flow，写用 suspend edit，别在界面里直接碰 DataStore。 */
class Store(private val context: Context) {

    private val data: DataStore<Preferences> get() = context.preferences

    val raw: Flow<Preferences> = data.data.catch { e ->
        // 读坏了（进程被杀时写一半）不能让整个流断掉，给一份空的继续跑
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    fun text(key: String): Flow<String?> = raw.map { it[stringPreferencesKey(key)] }

    suspend fun setText(key: String, value: String?) {
        data.edit { p ->
            val k = stringPreferencesKey(key)
            if (value == null) p.remove(k) else p[k] = value
        }
    }

    suspend fun clearAll() = data.edit { it.clear() }

    companion object {
        // 键名集中在这儿，散落各处的字符串键是下一版的坑
        const val KEY_TOKEN = "auth.token"
        const val KEY_USER_CODE = "auth.userCode"
        const val KEY_THEME_MODE = "theme.mode"
        const val KEY_ONBOARDED = "app.onboarded"
    }
}
