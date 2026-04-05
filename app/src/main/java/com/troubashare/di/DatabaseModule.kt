package com.troubashare.di

import android.content.Context
import androidx.room.Room
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.dao.*
import com.troubashare.data.repository.*
import com.troubashare.data.file.FileManager
import com.troubashare.data.sync.DeviceManager
import com.troubashare.data.sync.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTroubaShareDatabase(
        @ApplicationContext context: Context
    ): TroubaShareDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            TroubaShareDatabase::class.java,
            TroubaShareDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideGroupDao(database: TroubaShareDatabase): GroupDao = database.groupDao()

    @Provides
    fun providePartDao(database: TroubaShareDatabase): PartDao = database.partDao()

    @Provides
    fun provideSongDao(database: TroubaShareDatabase): SongDao = database.songDao()

    @Provides
    fun provideFileSelectionDao(database: TroubaShareDatabase): FileSelectionDao = database.fileSelectionDao()

    @Provides
    fun provideSetlistDao(database: TroubaShareDatabase): SetlistDao = database.setlistDao()

    @Provides
    fun provideAnnotationDao(database: TroubaShareDatabase): AnnotationDao = database.annotationDao()

    @Provides
    fun provideChangeLogDao(database: TroubaShareDatabase): ChangeLogDao = database.changeLogDao()

    @Provides
    @Singleton
    fun provideDeviceManager(@ApplicationContext context: Context): DeviceManager {
        return DeviceManager(context)
    }

    @Provides
    @Singleton
    fun provideGroupRepository(database: TroubaShareDatabase): GroupRepository {
        return GroupRepository(database)
    }

    @Provides
    @Singleton
    fun providePartRepository(database: TroubaShareDatabase): PartRepository {
        return PartRepository(database)
    }

    @Provides
    @Singleton
    fun provideFileManager(@ApplicationContext context: Context): FileManager {
        return FileManager(context)
    }

    @Provides
    @Singleton
    fun provideAnnotationRepository(database: TroubaShareDatabase): AnnotationRepository {
        return AnnotationRepository(database)
    }

    @Provides
    @Singleton
    fun provideFileSelectionRepository(database: TroubaShareDatabase): FileSelectionRepository {
        return FileSelectionRepository(database)
    }

    @Provides
    @Singleton
    fun provideSongRepository(
        database: TroubaShareDatabase,
        fileManager: FileManager,
        annotationRepository: AnnotationRepository
    ): SongRepository {
        return SongRepository(database, fileManager, annotationRepository)
    }

    @Provides
    @Singleton
    fun provideSetlistRepository(
        database: TroubaShareDatabase,
        songRepository: SongRepository
    ): SetlistRepository {
        return SetlistRepository(database, songRepository)
    }

    @Provides
    @Singleton
    fun provideSyncManager(
        @ApplicationContext context: Context,
        deviceManager: DeviceManager,
        groupRepository: GroupRepository,
        songRepository: SongRepository,
        annotationRepository: AnnotationRepository
    ): SyncManager {
        return SyncManager(context, deviceManager, groupRepository, songRepository, annotationRepository)
    }
}
