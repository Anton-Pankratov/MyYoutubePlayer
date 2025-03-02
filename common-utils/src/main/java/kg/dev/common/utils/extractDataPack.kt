package kg.dev.common.utils

import com.google.gson.internal.LinkedTreeMap
import java.util.*

inline fun <reified T> List<*>.mapDataPack(key: String): T? {
    val linkedTreeMaps = this.filterIsInstance<LinkedTreeMap<String, Any>>()
    return linkedTreeMaps.extractDataPack(key)
}

inline fun <reified T> List<LinkedTreeMap<String, Any>>.extractDataPack(key: String): T? {
    val stack = ArrayDeque<Any>()
    stack.addAll(this)

    while (stack.isNotEmpty()) {
        when (val current = stack.pop()) {
            is LinkedTreeMap<*, *> -> {
                if (key in current) return current[key] as? T
                current.values.forEach { stack.push(it) }
            }
            is List<*> -> {
                current.forEach { stack.push(it) }
            }
        }
    }

    return null
}