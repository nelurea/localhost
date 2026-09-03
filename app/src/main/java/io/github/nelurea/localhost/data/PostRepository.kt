package io.github.nelurea.localhost.data

import androidx.room.withTransaction

class PostRepository(
    private val database: LocalhostDatabase,
    private val postDao: PostDao,
    private val postImageDao: PostImageDao
) {
    val posts = postDao.observeAll()

    val postsWithImages =
        postDao.observeAllWithImages()

    suspend fun addPost(
        text: String,
        imagePath: String? = null
    ) {
        val imagePaths =
            imagePath?.let(::listOf)
                ?: emptyList()

        addPost(
            text = text,
            imagePaths = imagePaths
        )
    }

    suspend fun addPost(
        text: String,
        imagePaths: List<String>
    ) {
        database.withTransaction {
            val postId = postDao.insert(
                PostEntity(
                    createdAt = System.currentTimeMillis(),
                    text = text,
                    imagePath = imagePaths.firstOrNull()
                )
            )

            if (imagePaths.isNotEmpty()) {
                postImageDao.insertAll(
                    imagePaths.mapIndexed { index, path ->
                        PostImageEntity(
                            postId = postId,
                            imagePath = path,
                            position = index
                        )
                    }
                )
            }
        }
    }

    suspend fun deletePost(postId: Long) {
        postDao.markDeleted(
            postId = postId,
            deletedAt = System.currentTimeMillis()
        )
    }

    suspend fun restorePost(postId: Long) {
        postDao.restore(postId)
    }
}
