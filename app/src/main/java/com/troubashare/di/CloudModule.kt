package com.troubashare.di

import android.content.Context
import com.troubashare.data.cloud.*
import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudModule {
    
    @Provides
    @Singleton
    fun provideGoogleDriveProvider(
        @ApplicationContext context: Context
    ): GoogleDriveProvider {
        return GoogleDriveProvider(context)
    }
    
    @Provides
    @Singleton
    fun provideChangeTracker(
        @ApplicationContext context: Context,
        database: TroubaShareDatabase
    ): ChangeTracker {
        return ChangeTracker(context, database)
    }
    
    @Provides
    @Singleton
    fun provideConflictResolver(
        database: TroubaShareDatabase
    ): ConflictResolver {
        return ConflictResolver(database)
    }
    
    @Provides
    @Singleton
    fun provideCloudSyncManager(
        @ApplicationContext context: Context,
        googleDriveProvider: GoogleDriveProvider,
        groupRepository: GroupRepository,
        songRepository: SongRepository,
        setlistRepository: SetlistRepository,
        annotationRepository: AnnotationRepository,
        changeTracker: ChangeTracker,
        conflictResolver: ConflictResolver
    ): CloudSyncManager {
        return CloudSyncManager(
            context,
            googleDriveProvider,
            groupRepository,
            songRepository,
            setlistRepository,
            annotationRepository,
            changeTracker,
            conflictResolver
        )
    }
}