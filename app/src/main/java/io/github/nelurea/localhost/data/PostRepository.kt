package io.github.nelurea.localhost.data

class PostRepository(
    private val postDao: PostDao
) {
    val posts = postDao.observeAll()

    suspend fun addPost(text: String) {
        postDao.insert(
            PostEntity(
                createdAt = System.currentTimeMillis(),
                text = text
            )
        )
    }
}
