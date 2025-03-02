package kg.dev.common

import kg.dev.common.logger.Logger
import kg.dev.common.logger.LoggerImpl
import org.koin.dsl.module

val loggerModule = module {
    single<Logger> { LoggerImpl() }
}