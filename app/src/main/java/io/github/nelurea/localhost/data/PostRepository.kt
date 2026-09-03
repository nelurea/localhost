package io.github.nelurea.localhost.data

class PostRepository(
    private val postDao: PostDao
) {
    val posts = postDao.observeAll()

    suspend fun addPost(
        text: String,
        imagePath: String? = null
    ) {
        postDao.insert(
            PostEntity(
                createdAt = System.currentTimeMillis(),
                text = text,
                imagePath = imagePath
            )
        )
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
