package kg.dev.common.usecase

import kg.dev.common.async.CoroutineScopeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

abstract class UseCase(scope: CoroutineScope? = null) : CoroutineScopeProvider {

    override val coroutineScope: CoroutineScope = scope ?: CoroutineScope(Dispatchers.Unconfined)
}