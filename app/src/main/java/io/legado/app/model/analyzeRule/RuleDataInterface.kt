package io.legado.app.model.analyzeRule

import java.util.concurrent.ConcurrentHashMap

interface RuleDataInterface {

    /**
     * 必须使用线程安全容器：渐进式目录加载 / 预下载 / 缓存服务会让多条规则的 JS
     * 并发读写同一个实例的 variableMap，普通 HashMap 会 CME 或静默丢数据，
     * 表现为书源变量（如 book.putVariable("custom")）写入偶尔失效。
     */
    val variableMap: ConcurrentHashMap<String, String>

    fun putVariable(key: String, value: String?): Boolean {
        val keyExist = variableMap.contains(key)
        return when {
            value == null -> {
                variableMap.remove(key)
                putBigVariable(key, null)
                keyExist
            }

            value.length < 10000 -> {
                putBigVariable(key, null)
                variableMap[key] = value
                true
            }

            else -> {
                variableMap.remove(key)
                putBigVariable(key, value)
                keyExist
            }
        }
    }

    fun putBigVariable(key: String, value: String?)

    fun getVariable(key: String): String {
        return variableMap[key] ?: getBigVariable(key) ?: ""
    }

    fun getBigVariable(key: String): String?

}