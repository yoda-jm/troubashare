package com.troubashare.di

import com.troubashare.data.repository.AnnotationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun annotationRepository(): AnnotationRepository
}
