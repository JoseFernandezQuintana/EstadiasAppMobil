package com.cecapi.app.core.util

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What the phone has left and what this app takes up. Sizes are in bytes. */
data class StorageUsage(
    val freeBytes: Long,
    val totalBytes: Long,
    val appBytes: Long,
    val photoCount: Int,
    val photoBytes: Long,
    val oldPhotoCount: Int,
    val oldPhotoBytes: Long,
) {
    val lowSpace: Boolean get() = freeBytes < StorageReport.LOW_SPACE_BYTES
}

/**
 * The storage check: free space on the phone, what the app itself uses, and the scanned photos that
 * the text reader and the environment assistant keep. Old photos can be deleted; nothing is deleted
 * unless the caller asks for it, and callers must ask the person first.
 */
@Singleton
class StorageReport @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val photoFolders: List<File>
        get() = PHOTO_FOLDER_NAMES.map { File(context.filesDir, it) }

    suspend fun read(): StorageUsage = withContext(Dispatchers.IO) {
        val stat = StatFs(context.filesDir.path)
        val photos = photoFolders.flatMap { it.walkTopDown().filter(File::isFile).toList() }
        val cutoff = System.currentTimeMillis() - OLD_PHOTO_MILLIS
        val old = photos.filter { it.lastModified() < cutoff }
        val databases = context.getDatabasePath("cecapi").parentFile
        StorageUsage(
            freeBytes = stat.availableBytes,
            totalBytes = stat.totalBytes,
            appBytes = folderSize(context.filesDir) + folderSize(context.cacheDir) + (databases?.let(::folderSize) ?: 0L),
            photoCount = photos.size,
            photoBytes = photos.sumOf { it.length() },
            oldPhotoCount = old.size,
            oldPhotoBytes = old.sumOf { it.length() },
        )
    }

    /** Deletes the scanned photos older than [OLD_PHOTO_DAYS] days. Returns how many files went and how many bytes came back. */
    suspend fun deleteOldPhotos(): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - OLD_PHOTO_MILLIS
        var count = 0
        var bytes = 0L
        photoFolders.flatMap { it.walkTopDown().filter(File::isFile).toList() }
            .filter { it.lastModified() < cutoff }
            .forEach { file ->
                val size = file.length()
                if (file.delete()) {
                    count++
                    bytes += size
                }
            }
        count to bytes
    }

    /** "1,2 GB" or "35 MB": short enough to be said out loud. */
    fun format(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format(Locale("es"), "%.1f gigabytes", mb / 1024.0)
        } else {
            String.format(Locale("es"), "%.0f megabytes", mb)
        }
    }

    /** The report as a sentence for the assistant to say. */
    fun describe(usage: StorageUsage): String = buildString {
        append("Al teléfono le quedan ${format(usage.freeBytes)} libres de ${format(usage.totalBytes)}. ")
        append("La aplicación ocupa ${format(usage.appBytes)}. ")
        append("Guarda ${usage.photoCount} fotos que ocupan ${format(usage.photoBytes)}. ")
        if (usage.lowSpace) append("Queda poco espacio. Te conviene liberar espacio. ")
    }

    private fun folderSize(folder: File): Long =
        if (!folder.exists()) 0L else folder.walkTopDown().filter(File::isFile).sumOf { it.length() }

    companion object {
        /** Below this much free space the app warns the person. */
        const val LOW_SPACE_BYTES = 500L * 1024 * 1024

        const val OLD_PHOTO_DAYS = 30
        private const val OLD_PHOTO_MILLIS = OLD_PHOTO_DAYS * 24L * 60 * 60 * 1000

        // Where the reader and Entorno save their photos (inside the app's private files folder).
        private val PHOTO_FOLDER_NAMES = listOf("cecapi_docs", "cecapi_entorno")
    }
}
