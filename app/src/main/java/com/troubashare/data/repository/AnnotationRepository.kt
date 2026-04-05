package com.troubashare.data.repository

import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.AnnotationEntity
import com.troubashare.data.database.entities.AnnotationStrokeEntity
import com.troubashare.data.database.entities.AnnotationPointEntity
import com.troubashare.domain.model.Annotation
import com.troubashare.domain.model.AnnotationScope
import com.troubashare.domain.model.AnnotationStroke
import com.troubashare.domain.model.AnnotationPoint
import com.troubashare.domain.model.DrawingTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class AnnotationRepository(
    private val database: TroubaShareDatabase
) {
    private val annotationDao = database.annotationDao()

    /** Personal annotations for a member (scope = PERSONAL). */
    fun getAnnotationsByFileAndMember(fileId: String, memberId: String): Flow<List<Annotation>> {
        return annotationDao.getAnnotationsByFileAndMember(fileId, memberId).map { entities ->
            entities.map { it.toDomain(getStrokesForAnnotation(it.id)) }
        }
    }

    suspend fun getAnnotationsByFileAndMemberOnce(fileId: String, memberId: String): List<Annotation> {
        return annotationDao.getAnnotationsByFileAndMemberOnce(fileId, memberId).map {
            it.toDomain(getStrokesForAnnotation(it.id))
        }
    }

    /**
     * All annotations visible to a member: their own + ALL-scoped + PART-scoped for their parts.
     */
    fun getVisibleAnnotations(
        fileId: String,
        memberId: String,
        partIds: List<String> = emptyList()
    ): Flow<List<Annotation>> {
        return annotationDao.getVisibleAnnotations(fileId, memberId, partIds).map { entities ->
            entities.map { it.toDomain(getStrokesForAnnotation(it.id)) }
        }
    }

    suspend fun getVisibleAnnotationsOnce(
        fileId: String,
        memberId: String,
        partIds: List<String> = emptyList()
    ): List<Annotation> {
        return annotationDao.getVisibleAnnotationsOnce(fileId, memberId, partIds).map {
            it.toDomain(getStrokesForAnnotation(it.id))
        }
    }

    suspend fun getAnnotationsByPage(fileId: String, memberId: String, pageNumber: Int): List<Annotation> {
        return annotationDao.getAnnotationsByFileAndMemberAndPage(fileId, memberId, pageNumber).map {
            it.toDomain(getStrokesForAnnotation(it.id))
        }
    }

    suspend fun createAnnotation(
        fileId: String,
        memberId: String,
        pageNumber: Int = 0,
        scope: AnnotationScope = AnnotationScope.PERSONAL,
        partId: String? = null
    ): Annotation {
        val now = System.currentTimeMillis()
        val entity = AnnotationEntity(
            id = UUID.randomUUID().toString(),
            fileId = fileId,
            memberId = memberId,
            pageNumber = pageNumber,
            scope = scope.name,
            partId = partId,
            createdAt = now,
            updatedAt = now
        )
        annotationDao.insertAnnotation(entity)
        return entity.toDomain(emptyList())
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
            scope = annotation.scope.name,
            partId = annotation.partId,
            createdAt = annotation.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        annotationDao.updateAnnotation(entity)
        return annotation.copy(updatedAt = entity.updatedAt)
    }

    suspend fun saveAnnotationWithStrokes(annotation: Annotation) {
        val entity = AnnotationEntity(
            id = annotation.id,
            fileId = annotation.fileId,
            memberId = annotation.memberId,
            pageNumber = annotation.pageNumber,
            scope = annotation.scope.name,
            partId = annotation.partId,
            createdAt = annotation.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        annotationDao.updateAnnotation(entity)

        val existingStrokes = annotationDao.getStrokesByAnnotation(annotation.id)
        existingStrokes.forEach { stroke ->
            annotationDao.deletePointsByStroke(stroke.id)
            annotationDao.deleteStroke(stroke)
        }

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
            scope = annotation.scope.name,
            partId = annotation.partId,
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
        annotationDao.deletePointsByStroke(stroke.id)
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
        return annotationDao.getStrokesByAnnotation(annotationId).map { strokeEntity ->
            val points = annotationDao.getPointsByStroke(strokeEntity.id).map { p ->
                AnnotationPoint(x = p.x, y = p.y, pressure = p.pressure, timestamp = p.timestamp)
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

private fun AnnotationEntity.toDomain(strokes: List<AnnotationStroke>) = Annotation(
    id = id,
    fileId = fileId,
    memberId = memberId,
    pageNumber = pageNumber,
    scope = try { AnnotationScope.valueOf(scope) } catch (e: Exception) { AnnotationScope.PERSONAL },
    partId = partId,
    strokes = strokes,
    createdAt = createdAt,
    updatedAt = updatedAt
)
