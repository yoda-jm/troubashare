package com.troubashare.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 11,
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

        /**
         * v9 → v10: Rename the synthetic "fallback-member" memberId to "_shared_".
         *
         * Before the isFileLevelView / forced-shared-layer logic existed, annotations
         * drawn from the file-pool view were stored with memberId = "fallback-member"
         * (the value FileResolver returns when no real memberId is passed).  All those
         * annotations are logically the group/shared layer, so we rename them here.
         * Legitimate personal annotations already carry the real member UUID.
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "UPDATE annotations SET memberId = '_shared_' WHERE memberId = 'fallback-member'"
                )
            }
        }

        /**
         * v10 → v11: Re-apply the fallback-member → _shared_ rename.
         *
         * The v9→v10 migration renamed existing rows, but if the DB was wiped
         * (destructive migration) and then new annotations were drawn before the
         * addStroke guard was fixed, fresh 'fallback-member' rows appeared at v10.
         * This migration cleans them up once more.  No schema change.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "UPDATE annotations SET memberId = '_shared_' WHERE memberId = 'fallback-member'"
                )
            }
        }

        @Volatile
        private var INSTANCE: TroubaShareDatabase? = null

        fun getInstance(context: Context): TroubaShareDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TroubaShareDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun getDatabase(context: Context): TroubaShareDatabase = getInstance(context)
    }
}
