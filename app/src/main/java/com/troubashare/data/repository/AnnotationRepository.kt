package com.troubashare.data.repository

import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.AnnotationEntity
import com.troubashare.data.database.entities.AnnotationStrokeEntity
import com.troubashare.data.database.entities.AnnotationPointEntity
import com.troubashare.domain.model.Annotation
import com.troubashare.domain.model.AnnotationStroke
import com.troubashare.domain.model.AnnotationPoint
import com.troubashare.domain.model.DrawingTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

class AnnotationRepository(
    private val database: TroubaShareDatabase
) {
    private val annotationDao = database.annotationDao()
    
    fun getAnnotationsByFileAndMember(fileId: String, memberId: String): Flow<List<Annotation>> {
        return annotationDao.getAnnotationsByFileAndMember(fileId, memberId).map { entities ->
            // Use runBlocking or better yet, restructure to use suspend context
            entities.map { entity ->
                kotlinx.coroutines.runBlocking {
                    val strokes = getStrokesForAnnotation(entity.id)
                    entity.toDomainModel(strokes)
                }
            }
        }
    }

    suspend fun getAnnotationsByFileAndMemberOnce(fileId: String, memberId: String): List<Annotation> {
        val entities = annotationDao.getAnnotationsByFileAndMemberOnce(fileId, memberId)
        return entities.map { entity ->
            val strokes = getStrokesForAnnotation(entity.id)
            entity.toDomainModel(strokes)
        }
    }
    
    suspend fun getAnnotationsByPage(fileId: String, memberId: String, pageNumber: Int): List<Annotation> {
        val entities = annotationDao.getAnnotationsByFileAndMemberAndPage(fileId, memberId, pageNumber)
        return entities.map { entity ->
            val strokes = getStrokesForAnnotation(entity.id)
            entity.toDomainModel(strokes)
        }
    }
    
    suspend fun createAnnotation(fileId: String, memberId: String, pageNumber: Int = 0): Annotation {
        val annotationId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        println("DEBUG AnnotationRepository: Creating annotation with fileId='$fileId', memberId='$memberId', pageNumber=$pageNumber")
        
        val entity = AnnotationEntity(
            id = annotationId,
            fileId = fileId,
            memberId = memberId,
            pageNumber = pageNumber,
            createdAt = now,
            updatedAt = now
        )
        
        try {
            annotationDao.insertAnnotation(entity)
            println("DEBUG AnnotationRepository: Successfully created annotation with id='$annotationId'")
            return entity.toDomainModel(emptyList())
        } catch (e: Exception) {
            println("DEBUG AnnotationRepository: Failed to create annotation - ${e.message}")
            throw e
        }
    }
    
    suspend fun addStrokeToAnnotation(annotationId: String, stroke: AnnotationStroke): AnnotationStroke {
        val strokeEntity = AnnotationStrokeEntity(
            id = stroke.id,
            annotationId = annotationId,
            color = stroke.color,
            strokeWidth = stroke.strokeWidth,
            opacity = stroke.opacity,
            tool = stroke.tool.name,
            text = stroke.text,
            createdAt = stroke.createdAt
        )

        annotationDao.insertStroke(strokeEntity)
        
        // Insert points for the stroke
        val pointEntities = stroke.points.map { point ->
            AnnotationPointEntity(
                strokeId = stroke.id,
                x = point.x,
                y = point.y,
                pressure = point.pressure,
                timestamp = point.timestamp
            )
        }
        
        if (pointEntities.isNotEmpty()) {
            annotationDao.insertPoints(pointEntities)
        }
        
        return stroke
    }
    
    suspend fun updateAnnotation(annotation: Annotation): Annotation {
        val entity = AnnotationEntity(
            id = annotation.id,
            fileId = annotation.fileId,
            memberId = annotation.memberId,
            pageNumber = annotation.pageNumber,
            createdAt = annotation.createdAt,
            updatedAt = System.currentTimeMillis()
        )

        annotationDao.updateAnnotation(entity)
        return annotation.copy(updatedAt = entity.updatedAt)
    }

    suspend fun saveAnnotationWithStrokes(annotation: Annotation) {
        // Update annotation metadata
        val entity = AnnotationEntity(
            id = annotation.id,
            fileId = annotation.fileId,
            memberId = annotation.memberId,
            pageNumber = annotation.pageNumber,
            createdAt = annotation.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        annotationDao.updateAnnotation(entity)

        // Delete all existing strokes and points for this annotation
        val existingStrokes = annotationDao.getStrokesByAnnotation(annotation.id)
        existingStrokes.forEach { stroke ->
            annotationDao.deletePointsByStroke(stroke.id)
            annotationDao.deleteStroke(stroke)
        }

        // Insert all current strokes with their points
        annotation.strokes.forEach { stroke ->
            val strokeEntity = AnnotationStrokeEntity(
                id = stroke.id,
                annotationId = annotation.id,
                color = stroke.color,
                strokeWidth = stroke.strokeWidth,
                opacity = stroke.opacity,
                tool = stroke.tool.name,
                text = stroke.text,
                createdAt = stroke.createdAt
            )
            annotationDao.insertStroke(strokeEntity)

            // Insert points
            val pointEntities = stroke.points.map { point ->
                AnnotationPointEntity(
                    strokeId = stroke.id,
                    x = point.x,
                    y = point.y,
                    pressure = point.pressure,
                    timestamp = point.timestamp
                )
            }
            if (pointEntities.isNotEmpty()) {
                annotationDao.insertPoints(pointEntities)
            }
        }
    }
    
    suspend fun deleteAnnotation(annotation: Annotation) {
        val entity = AnnotationEntity(
            id = annotation.id,
            fileId = annotation.fileId,
            memberId = annotation.memberId,
            pageNumber = annotation.pageNumber,
            createdAt = annotation.createdAt,
            updatedAt = annotation.updatedAt
        )
        
        annotationDao.deleteAnnotation(entity)
    }
    
    suspend fun deleteStroke(strokeId: String) {
        val stroke = annotationDao.getStrokeById(strokeId)
        if (stroke != null) {
            annotationDao.deleteStroke(stroke)
        }
    }
    
    suspend fun clearAnnotationsForFile(fileId: String) {
        annotationDao.deleteAnnotationsByFile(fileId)
    }
    
    suspend fun removeStrokeFromAnnotation(annotationId: String, stroke: AnnotationStroke) {
        // Delete points for the stroke
        annotationDao.deletePointsByStroke(stroke.id)

        // Delete the stroke entity
        val strokeEntity = AnnotationStrokeEntity(
            id = stroke.id,
            annotationId = annotationId,
            color = stroke.color,
            strokeWidth = stroke.strokeWidth,
            opacity = stroke.opacity,
            tool = stroke.tool.name,
            text = stroke.text,
            createdAt = stroke.createdAt
        )
        annotationDao.deleteStroke(strokeEntity)
    }
    
    private suspend fun getStrokesForAnnotation(annotationId: String): List<AnnotationStroke> {
        val strokeEntities = annotationDao.getStrokesByAnnotation(annotationId)
        return strokeEntities.map { strokeEntity ->
            val pointEntities = annotationDao.getPointsByStroke(strokeEntity.id)
            val points = pointEntities.map { pointEntity ->
                AnnotationPoint(
                    x = pointEntity.x,
                    y = pointEntity.y,
                    pressure = pointEntity.pressure,
                    timestamp = pointEntity.timestamp
                )
            }
            
            AnnotationStroke(
                id = strokeEntity.id,
                points = points,
                color = strokeEntity.color,
                strokeWidth = strokeEntity.strokeWidth,
                opacity = strokeEntity.opacity,
                tool = DrawingTool.valueOf(strokeEntity.tool),
                text = strokeEntity.text,
                createdAt = strokeEntity.createdAt
            )
        }
    }
}

// Extension functions for conversion
private fun AnnotationEntity.toDomainModel(strokes: List<AnnotationStroke>): Annotation {
    return Annotation(
        id = id,
        fileId = fileId,
        memberId = memberId,
        pageNumber = pageNumber,
        strokes = strokes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}