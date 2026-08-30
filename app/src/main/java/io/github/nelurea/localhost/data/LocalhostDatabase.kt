package io.github.nelurea.localhost.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PostEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LocalhostDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao

    companion object {
        @Volatile
        private var instance: LocalhostDatabase? = null

        fun getInstance(context: Context): LocalhostDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalhostDatabase::class.java,
                    "localhost.db"
                ).build().also { instance = it }
            }
    }
}
