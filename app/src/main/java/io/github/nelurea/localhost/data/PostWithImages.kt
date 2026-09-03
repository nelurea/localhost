package io.github.nelurea.localhost.data

import androidx.room.Embedded
import androidx.room.Relation

data class PostWithImages(
    @Embedded
    val post: PostEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "postId"
    )
    val images: List<PostImageEntity>
)
