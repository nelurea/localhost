package io.github.nelurea.localhost.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<PostEntity>>

    @Insert
    suspend fun insert(post: PostEntity)
}
