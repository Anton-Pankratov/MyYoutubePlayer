package kg.dev.common.events

import kotlinx.coroutines.flow.StateFlow

interface EventManager {

     val event: StateFlow<Event>

    fun post(event: Event)
}