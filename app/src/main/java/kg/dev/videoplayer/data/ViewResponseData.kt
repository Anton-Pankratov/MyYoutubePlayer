package kg.dev.videoplayer.data

sealed class ViewResponseData {

    class Loading : ViewResponseData()

    class NoData : ViewResponseData()

    data class Success<T>(
        val data: T
    ) : ViewResponseData()

    data class Error(
        val message: String,
        val code: Int
    ) : ViewResponseData()
}