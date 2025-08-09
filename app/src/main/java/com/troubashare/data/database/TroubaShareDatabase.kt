package com.troubashare.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.troubashare.data.database.dao.GroupDao
import com.troubashare.data.database.dao.SongDao
import com.troubashare.data.database.dao.SetlistDao
import com.troubashare.data.database.dao.AnnotationDao
import com.troubashare.data.database.entities.*

@Database(
    entities = [
        GroupEntity::class,
        MemberEntity::class,
        SongEntity::class,
        SongFileEntity::class,
        SetlistEntity::class,
        SetlistItemEntity::class,
        AnnotationEntity::class,
        AnnotationStrokeEntity::class,
        AnnotationPointEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class TroubaShareDatabase : RoomDatabase() {
    
    abstract fun groupDao(): GroupDao
    abstract fun songDao(): SongDao
    abstract fun setlistDao(): SetlistDao
    abstract fun annotationDao(): AnnotationDao
    
    companion object {
        const val DATABASE_NAME = "troubashare_database"
        
        @Volatile
        private var INSTANCE: TroubaShareDatabase? = null
        
        fun getInstance(context: Context): TroubaShareDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TroubaShareDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        fun getDatabase(context: Context): TroubaShareDatabase = getInstance(context)
    }
}