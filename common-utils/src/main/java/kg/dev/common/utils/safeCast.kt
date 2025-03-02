package kg.dev.common.utils

inline fun <reified T> List<*>?.safeCast(): List<T> {
    return this?.mapNotNull { it as? T } ?: emptyList()
}

inline fun <reified T> Any?.safeCast(): T? {
    return this as? T
}