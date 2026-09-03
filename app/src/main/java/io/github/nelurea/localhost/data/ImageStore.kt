package io.github.nelurea.localhost.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

class ImageStore(
    private val context: Context
) {
    private val imageDirectory: File
        get() = File(
            context.filesDir,
            "post_images"
        ).apply {
            mkdirs()
        }

    fun importImage(uri: Uri): String {
        val finalFile = File(
            imageDirectory,
            UUID.randomUUID().toString()
        )

        val temporaryFile = File(
            imageDirectory,
            "${finalFile.name}.tmp"
        )

        try {
            context.contentResolver
                .openInputStream(uri)
                ?.use { input ->
                    temporaryFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                ?: throw IllegalStateException(
                    "Unable to open selected image."
                )

            if (!temporaryFile.renameTo(finalFile)) {
                throw IllegalStateException(
                    "Unable to store selected image."
                )
            }

            return finalFile.absolutePath
        } catch (error: Exception) {
            temporaryFile.delete()
            finalFile.delete()
            throw error
        }
    }

    fun delete(path: String) {
        File(path).delete()
    }
}
