package kg.dev.common.network.services

import okhttp3.ResponseBody
import retrofit2.Response

fun <T> handleApiResponse(
    apiResponse: Response<ResponseBody>,
    parseResponse: (String) -> T
): ServiceResponse<T> {
    return try {
        if (apiResponse.isSuccessful) {
            val responseBody = apiResponse.body()?.string()
            if (responseBody != null) {
                val parsedData = parseResponse(responseBody)
                ServiceResponse.success(parsedData, apiResponse.code())
            } else {
                ServiceResponse.error("Response body is null", apiResponse.code())
            }
        } else {
            val errorMessage = apiResponse.errorBody()?.string() ?: "Unknown error"
            ServiceResponse.error(errorMessage, apiResponse.code())
        }
    } catch (e: Exception) {
        ServiceResponse.error(e.localizedMessage ?: "Unknown error", -1)
    }
}