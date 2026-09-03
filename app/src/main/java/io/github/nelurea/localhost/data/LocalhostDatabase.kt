package io.github.nelurea.localhost.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        PostEntity::class,
        PostImageEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class LocalhostDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun postImageDao(): PostImageDao

    companion object {
        @Volatile
        private var instance: LocalhostDatabase? = null

        private val MIGRATION_1_2 = Migration(1, 2) { database ->
            database.execSQL(
                "ALTER TABLE posts ADD COLUMN deletedAt INTEGER"
            )
        }

        private val MIGRATION_2_3 = Migration(2, 3) { database ->
            database.execSQL(
                "ALTER TABLE posts ADD COLUMN imagePath TEXT"
            )
        }

        private val MIGRATION_3_4 = Migration(3, 4) { database ->
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS post_images (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    postId INTEGER NOT NULL,
                    imagePath TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    FOREIGN KEY(postId)
                        REFERENCES posts(id)
                        ON UPDATE NO ACTION
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                    index_post_images_postId
                ON post_images(postId)
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                    index_post_images_postId_position
                ON post_images(postId, position)
                """.trimIndent()
            )

            database.execSQL(
                """
                INSERT INTO post_images (
                    postId,
                    imagePath,
                    position
                )
                SELECT
                    id,
                    imagePath,
                    0
                FROM posts
                WHERE imagePath IS NOT NULL
                """.trimIndent()
            )
        }

        fun getInstance(context: Context): LocalhostDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalhostDatabase::class.java,
                    "localhost.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
