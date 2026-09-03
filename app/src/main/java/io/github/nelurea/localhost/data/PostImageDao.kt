package io.github.nelurea.localhost.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostImageDao {
    @Insert
    suspend fun insertAll(images: List<PostImageEntity>)

    @Query(
        """
        SELECT * FROM post_images
        WHERE postId = :postId
        ORDER BY position ASC, id ASC
        """
    )
    fun observeForPost(
        postId: Long
    ): Flow<List<PostImageEntity>>

    @Query(
        """
        SELECT * FROM post_images
        WHERE postId = :postId
        ORDER BY position ASC, id ASC
        """
    )
    suspend fun getForPost(
        postId: Long
    ): List<PostImageEntity>
}
