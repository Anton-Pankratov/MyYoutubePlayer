package kg.dev.common.logger

interface Logger {

    fun init()

    fun debug(msg: String)

    fun error(msg: String)

    fun error(e: Throwable)
}