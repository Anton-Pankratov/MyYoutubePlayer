package kg.dev.common.utils

interface Mapper<From, To> {

    fun convert(source: From): To
}