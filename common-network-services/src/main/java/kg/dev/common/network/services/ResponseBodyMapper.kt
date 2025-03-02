package kg.dev.common.network.services

import com.google.gson.Gson
import kg.dev.common.utils.Mapper
import okhttp3.ResponseBody
import retrofit2.Response

class ResponseBodyMapper<T>(private val clazz: Class<T>) : Mapper<Response<ResponseBody>, ServiceResponse<T>> {
    override fun convert(source: Response<ResponseBody>): ServiceResponse<T> {
        return if (source.isSuccessful) {
            try {
                val responseBody = source.body()?.string()
                val data = Gson().fromJson(responseBody, clazz)
                ServiceResponse.success(data, source.code())
            } catch (e: Exception) {
                ServiceResponse.error("Ошибка при обработке данных", source.code())
            }
        } else {
            ServiceResponse.error("Ошибка при запросе", source.code())
        }
    }
}