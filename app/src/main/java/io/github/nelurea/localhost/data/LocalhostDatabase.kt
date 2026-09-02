package io.github.nelurea.localhost.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [PostEntity::class],
    version = 2,
    exportSchema = false
)
abstract class LocalhostDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao

    companion object {
        @Volatile
        private var instance: LocalhostDatabase? = null

        private val MIGRATION_1_2 = Migration(1, 2) { database ->
            database.execSQL(
                "ALTER TABLE posts ADD COLUMN deletedAt INTEGER"
            )
        }

        fun getInstance(context: Context): LocalhostDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalhostDatabase::class.java,
                    "localhost.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
