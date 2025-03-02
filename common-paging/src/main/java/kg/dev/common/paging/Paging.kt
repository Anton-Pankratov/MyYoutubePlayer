package kg.dev.common.paging

interface Paging<ViewData> {

    val action: (suspend (String?) -> List<ViewData>)?
}