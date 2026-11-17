package com.clippilot.android

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

/** Real local MP4 assembler: concatenates source items in their selected order. */
@OptIn(markerClass = [UnstableApi::class])
internal class ExportVideoEngine(private val context: Context) {
    private var transformer: Transformer? = null
    private var running = false
    var currentOutput: File? = null
        private set

    fun start(
        videos: List<Uri>,
        onCompleted: (File) -> Unit,
        onFailed: (String) -> Unit
    ) {
        check(!running) { "Exportação já em andamento" }
        require(videos.size == 3) { "Selecione três vídeos para exportar." }

        val folder = File(context.filesDir, "exports").apply { mkdirs() }
        val destination = File(folder, "clippilot-${System.currentTimeMillis()}.mp4")
        currentOutput = destination
        try {
            val items = videos.map { uri ->
                EditedMediaItem.Builder(MediaItem.fromUri(uri)).build()
            }
            // Audio and video from each clip are kept together in sequence.
            val sequence = EditedMediaItemSequence.Builder(items).build()
            val composition = Composition.Builder(sequence).build()
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    running = false
                    if (destination.isFile && destination.length() > 1024L && validMp4(destination)) {
                        context.getSharedPreferences("clippilot-project", Context.MODE_PRIVATE)
                            .edit().putString("lastExport", destination.absolutePath).apply()
                        onCompleted(destination)
                    } else {
                        destination.delete()
                        onFailed("O MP4 exportado não passou na verificação.")
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    running = false
                    destination.delete()
                    onFailed(exportException.message ?: "Erro no processamento do vídeo.")
                }
            }
            transformer = Transformer.Builder(context).addListener(listener).build()
            running = true
            transformer!!.start(composition, destination.absolutePath)
        } catch (t: Exception) {
            running = false
            destination.delete()
            onFailed(t.message ?: "Não foi possível iniciar a exportação.")
        }
    }

    fun percent(): Int? {
        if (!running) return null
        return try {
            val holder = ProgressHolder()
            if (transformer?.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                holder.progress.coerceIn(0, 99)
            } else null
        } catch (_: Exception) { null }
    }

    fun cancel() {
        if (running) {
            transformer?.cancel()
            currentOutput?.delete()
            running = false
        }
    }

    private fun validMp4(file: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) > 0L
        } catch (_: Exception) { false }
        finally { runCatching { retriever.release() } }
    }

    companion object {
        /** Expose completed MP4 in the user's Movies/ClipPilot folder, Android 10+. */
        fun saveToGallery(context: Context, file: File): Uri? {
            if (!file.isFile || file.length() == 0L) return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ClipPilot")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            return try {
                resolver.openOutputStream(uri)?.use { stream ->
                    file.inputStream().use { it.copyTo(stream) }
                } ?: error("Não foi possível abrir o destino.")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                uri
            } catch (_: Exception) {
                resolver.delete(uri, null, null)
                null
            }
        }
    }
}
