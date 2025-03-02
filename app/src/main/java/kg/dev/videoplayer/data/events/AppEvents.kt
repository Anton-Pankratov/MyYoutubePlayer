package kg.dev.videoplayer.data.events

import kg.dev.common.events.Event

sealed class AppEvents : Event {

    sealed class Channel : AppEvents() {

        sealed class CallNextPage : Channel() {

            object Loading : CallNextPage()

            object Success : CallNextPage()

            class Error(error: Throwable) : CallNextPage()
        }
    }
}