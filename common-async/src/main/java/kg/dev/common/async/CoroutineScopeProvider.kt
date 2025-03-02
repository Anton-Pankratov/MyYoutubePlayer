package kg.dev.common.async

import kotlinx.coroutines.CoroutineScope

interface CoroutineScopeProvider {

    val coroutineScope: CoroutineScope
}