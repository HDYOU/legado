package io.legado.app.model.analyzeRule

import io.legado.app.utils.GSON
import java.util.concurrent.ConcurrentHashMap

class RuleData : RuleDataInterface {

    override val variableMap by lazy {
        ConcurrentHashMap<String, String>()
    }

    override fun putBigVariable(key: String, value: String?) {
        if (value == null) {
            variableMap.remove(key)
        } else {
            variableMap[key] = value
        }
    }

    override fun getBigVariable(key: String): String? {
        return null
    }

    fun getVariable(): String? {
        if (variableMap.isEmpty()) {
            return null
        }
        return GSON.toJson(variableMap)
    }

}