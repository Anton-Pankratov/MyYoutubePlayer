package kg.dev.common.network.services

import kg.dev.common.logger.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class ServiceResponse<T>(
    val data: T? = null,
    val code: Int,
    val message: String,
    val isSuccessful: Boolean
) {

    companion object : KoinComponent {

        private val logger by inject<Logger>()

        fun <T> success(data: T?, code: Int): ServiceResponse<T> {
            logger.debug(msg = "Сode: $code. Response: ${data.toString()}")
            return ServiceResponse(
                data = data,
                code = code,
                message = "Success",
                isSuccessful = true
            )
        }

        fun <T> error(message: String, code: Int): ServiceResponse<T> {
            logger.error("Code: $code. $message")
            return ServiceResponse(
                data = null,
                code = code,
                message = message,
                isSuccessful = false
            )
        }
    }
}