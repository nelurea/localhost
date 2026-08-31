package io.github.nelurea.localhost.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStreamWriter

class DraftStore(context: Context) {
    private val file = AtomicFile(
        File(context.filesDir, "composer_draft.txt")
    )

    fun read(): String {
        return try {
            file.openRead()
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (_: FileNotFoundException) {
            ""
        }
    }

    fun write(text: String) {
        val output = file.startWrite()

        try {
            val writer = OutputStreamWriter(output, Charsets.UTF_8)
            writer.write(text)
            writer.flush()
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    fun clear() {
        file.delete()
    }
}
