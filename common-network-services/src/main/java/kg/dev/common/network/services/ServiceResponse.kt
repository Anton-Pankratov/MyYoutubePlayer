package kg.dev.common.network.services

data class ServiceResponse<T>(
    val data: T? = null,
    val code: Int,
    val message: String,
    val isSuccessful: Boolean
) {
    companion object {
        fun <T> success(data: T?, code: Int): ServiceResponse<T> {
            return ServiceResponse(
                data = data,
                code = code,
                message = "Success",
                isSuccessful = true
            )
        }

        fun <T> error(message: String, code: Int): ServiceResponse<T> {
            return ServiceResponse(
                data = null,
                code = code,
                message = message,
                isSuccessful = false
            )
        }
    }
}