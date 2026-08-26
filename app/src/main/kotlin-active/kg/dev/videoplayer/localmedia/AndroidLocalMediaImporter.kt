package kg.dev.videoplayer.localmedia

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.player.DirectMediaDescriptor
import kg.dev.shared.feature.player.DirectMediaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface LocalMediaImportResult {
    data class Success(val media: MediaCatalogItem) : LocalMediaImportResult
    data class Failure(val message: String) : LocalMediaImportResult
}

internal data class ImportedLocalMediaMetadata(
    val title: String,
    val uri: String,
    val mimeType: String?,
    val durationMs: Long?
) {
    fun toDirectDescriptor(externalId: String) = DirectMediaDescriptor(
        id = externalId,
        title = title,
        uri = uri,
        mimeType = mimeType,
        durationMs = durationMs
    )
}

/**
 * Android-only boundary between the system document picker and the shared durable Direct provider.
 * OpenDocument grants persistable read access; the provider stores only a stable application ID
 * as media identity and keeps the content URI as the resolvable playback source.
 */
class AndroidLocalMediaImporter(
    private val context: Context,
    private val directMediaProvider: DirectMediaProvider
) {
    suspend fun import(uri: Uri): LocalMediaImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            if (!persistAndVerifyReadAccess(resolver, uri)) {
                return@runCatching LocalMediaImportResult.Failure("Unable to access this video.")
            }

            val mimeType = resolver.getType(uri)
            if (mimeType != null && !mimeType.startsWith(VIDEO_MIME_PREFIX)) {
                return@runCatching LocalMediaImportResult.Failure("Please choose a video file.")
            }

            val externalId = UUID.randomUUID().toString()
            val title = resolver.displayName(uri) ?: "Imported video"
            val durationMs = context.durationOf(uri)
            val descriptor = ImportedLocalMediaMetadata(
                title = title,
                uri = uri.toString(),
                mimeType = mimeType,
                durationMs = durationMs
            ).toDirectDescriptor(externalId)
            directMediaProvider.register(descriptor)
            LocalMediaImportResult.Success(
                MediaCatalogItem(
                    reference = MediaReference(MediaProviders.Direct, externalId),
                    title = title,
                    durationMs = durationMs
                )
            )
        }.getOrElse { LocalMediaImportResult.Failure("Unable to import this video.") }
    }

    private fun persistAndVerifyReadAccess(resolver: ContentResolver, uri: Uri): Boolean = runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (!resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) return@runCatching false
        resolver.openFileDescriptor(uri, "r")?.use { } ?: return@runCatching false
        true
    }.getOrDefault(false)

    private fun ContentResolver.displayName(uri: Uri): String? = query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (column >= 0 && cursor.moveToFirst()) cursor.getString(column)?.takeIf(String::isNotBlank) else null
    }

    private fun Context.durationOf(uri: Uri): Long? = runCatching {
        MediaMetadataRetriever().let { retriever ->
            try {
                retriever.setDataSource(this, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
            } finally {
                retriever.release()
            }
        }
    }.getOrNull()

    private companion object {
        const val VIDEO_MIME_PREFIX = "video/"
    }
}
