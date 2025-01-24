package kg.dev.common.utils

interface Mapper<From, To> {

    fun convert(from: From): To
}