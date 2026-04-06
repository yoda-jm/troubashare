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
        AnnotationLayerEntity::class,
        ChangeLogEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class TroubaShareDatabase : RoomDatabase() {

    abstract fun groupDao(): GroupDao
    abstract fun partDao(): PartDao
    abstract fun songDao(): SongDao
    abstract fun fileSelectionDao(): FileSelectionDao
    abstract fun setlistDao(): SetlistDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun annotationLayerDao(): AnnotationLayerDao
    abstract fun changeLogDao(): ChangeLogDao

    companion object {
        const val DATABASE_NAME = "troubashare_database"

        /** v9→v10: rename legacy "fallback-member" memberId to "_shared_". */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "UPDATE annotations SET memberId = '_shared_' WHERE memberId = 'fallback-member'"
                )
            }
        }

        /**
         * v10→v11: Re-apply fallback-member → _shared_ rename.
         * Needed because the v9→v10 migration didn't run if the DB was wiped,
         * and new annotations were still being saved as "fallback-member" before
         * the addStroke guard was fixed.
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "UPDATE annotations SET memberId = '_shared_' WHERE memberId = 'fallback-member'"
                )
            }
        }

        /**
         * v11→v12: Add multi-layer support.
         *
         * 1. Create annotation_layers table.
         * 2. Add layerId column to annotations.
         * 3. For each distinct (fileId, memberId) in annotations, create a default
         *    layer using a deterministic ID = fileId || '_' || memberId so the UPDATE
         *    in step 4 can reference it without generating UUIDs in SQL.
         * 4. Point every existing annotation row at its default layer.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create annotation_layers table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS annotation_layers (
                        id TEXT NOT NULL PRIMARY KEY,
                        fileId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        ownerId TEXT NOT NULL,
                        colorIndex INTEGER NOT NULL DEFAULT 0,
                        displayOrder INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_annotation_layers_fileId ON annotation_layers(fileId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_annotation_layers_ownerId ON annotation_layers(ownerId)")

                // 2. Add layerId column
                database.execSQL("ALTER TABLE annotations ADD COLUMN layerId TEXT NOT NULL DEFAULT ''")

                // 3. Create one default layer per distinct (fileId, memberId) combo
                database.execSQL("""
                    INSERT OR IGNORE INTO annotation_layers (id, fileId, name, ownerId, colorIndex, displayOrder, createdAt)
                    SELECT DISTINCT
                        fileId || '_' || memberId,
                        fileId,
                        CASE WHEN memberId = '_shared_' THEN 'Group' ELSE 'Personal' END,
                        memberId,
                        0,
                        CASE WHEN memberId = '_shared_' THEN 1 ELSE 0 END,
                        strftime('%s','now') * 1000
                    FROM annotations
                    WHERE fileId != '' AND memberId != ''
                """.trimIndent())

                // 4. Point annotations at their default layer
                database.execSQL("""
                    UPDATE annotations
                    SET layerId = fileId || '_' || memberId
                    WHERE fileId != '' AND memberId != ''
                """.trimIndent())
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
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun getDatabase(context: Context): TroubaShareDatabase = getInstance(context)
    }
}
