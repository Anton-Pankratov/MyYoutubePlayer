package kg.dev.common.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState

class PagingSourceImpl<Data : Any> : PagingSource<String, Data>(), Paging<Data> {

    override val action: (suspend (String?) -> List<Data>)? = null

    private var state: ((Throwable) -> Unit)? = null

    override fun getRefreshKey(state: PagingState<String, Data>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Data> {
        return try {
            action?.let { action ->
                LoadResult.Page(
                    data = action.invoke(params.key),
                    prevKey = null,
                    nextKey = params.key
                )
            } ?: LoadResult.Error<String, Data>(ClassCastException("This is not a"))
                .also {
                    state?.invoke(ClassCastException("This is not a List<ChannelViewData>!"))
                }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}