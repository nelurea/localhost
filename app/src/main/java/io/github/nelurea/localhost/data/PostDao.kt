package io.github.nelurea.localhost.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query(
        """
        SELECT * FROM posts
        WHERE deletedAt IS NULL
        ORDER BY createdAt DESC, id DESC
        """
    )
    fun observeAll(): Flow<List<PostEntity>>

    @Insert
    suspend fun insert(post: PostEntity)

    @Query(
        """
        UPDATE posts
        SET deletedAt = :deletedAt
        WHERE id = :postId
        """
    )
    suspend fun markDeleted(
        postId: Long,
        deletedAt: Long
    )

    @Query(
        """
        UPDATE posts
        SET deletedAt = NULL
        WHERE id = :postId
        """
    )
    suspend fun restore(postId: Long)
}
