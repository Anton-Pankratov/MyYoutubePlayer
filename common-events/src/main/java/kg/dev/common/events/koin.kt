package kg.dev.common.events

import org.koin.dsl.module

val eventsModule = module {
    single<EventManager> { EventManagerImpl() }
}