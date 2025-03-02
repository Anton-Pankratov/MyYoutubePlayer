package kg.dev.core.repositories.youtube.channel

import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import kg.dev.common.network.api.response.ApiResponse
import kg.dev.common.network.services.ServiceResponse
import kg.dev.common.utils.Mapper

class ServiceResponseMapper<I>(private val clazz: Class<I>) :
    Mapper<ServiceResponse<ApiResponse<*>>, ApiResponse<I>> {

    override fun convert(source: ServiceResponse<ApiResponse<*>>): ApiResponse<I> {
        return if (source.isSuccessful) {
            val gson = Gson()
            val itemsList =
                source.data?.items as? ArrayList<LinkedTreeMap<String, Any>> ?: emptyList()

            val itemsJson = gson.toJson(itemsList)
            val listType = TypeToken.getParameterized(List::class.java, clazz).type
            val items: List<I> = gson.fromJson(itemsJson, listType)

            ApiResponse.success(
                kind = source.data?.kind,
                etag = source.data?.etag,
                nextPageToken = source.data?.nextPageToken,
                regionCode = source.data?.regionCode,
                pageInfo = source.data?.pageInfo,
                items = items
            )
        } else {
            ApiResponse.error(e = Throwable(source.message))
        }
    }
}