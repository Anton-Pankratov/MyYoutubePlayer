package kg.dev.common.logger

import timber.log.Timber

class LoggerImpl : Logger {

    override fun init() {
        Timber.plant(Timber.DebugTree())
    }

    override fun debug(msg: String) {
        Timber.d(msg)
    }

    override fun error(msg: String) {
        Timber.e(msg)
    }

    override fun error(e: Throwable) {
        Timber.e(e)
    }
}