package kg.dev.common.events

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class EventManagerImpl : EventManager {

    private var _event = MutableStateFlow<Event>(NoEvent)
    override val event: StateFlow<Event> = _event

    override fun post(event: Event) {
        _event.tryEmit(event)
    }
}