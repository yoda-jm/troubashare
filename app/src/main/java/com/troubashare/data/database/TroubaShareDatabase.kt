package com.troubashare.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.troubashare.data.database.dao.*
import com.troubashare.data.database.entities.*

@Database(
    entities = [
        GroupEntity::class,
        MemberEntity::class,
        PartEntity::class,
        SongEntity::class,
        SongFileEntity::class,
        FileSelectionEntity::class,
        SetlistEntity::class,
        SetlistItemEntity::class,
        AnnotationEntity::class,
        AnnotationStrokeEntity::class,
        AnnotationPointEntity::class,
        ChangeLogEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class TroubaShareDatabase : RoomDatabase() {

    abstract fun groupDao(): GroupDao
    abstract fun partDao(): PartDao
    abstract fun songDao(): SongDao
    abstract fun fileSelectionDao(): FileSelectionDao
    abstract fun setlistDao(): SetlistDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun changeLogDao(): ChangeLogDao

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
