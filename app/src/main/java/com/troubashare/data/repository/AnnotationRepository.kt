package com.troubashare.data.repository

import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.AnnotationEntity
import com.troubashare.data.database.entities.AnnotationLayerEntity
import com.troubashare.data.database.entities.AnnotationStrokeEntity
import com.troubashare.data.database.entities.AnnotationPointEntity
import com.troubashare.domain.model.Annotation
import com.troubashare.domain.model.AnnotationLayer
import com.troubashare.domain.model.AnnotationScope
import com.troubashare.domain.model.AnnotationStroke
import com.troubashare.domain.model.AnnotationPoint
import com.troubashare.domain.model.DrawingTool
import com.troubashare.domain.model.SHARED_ANNOTATION_LAYER
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class AnnotationRepository(
    private val database: TroubaShareDatabase
) {
    private val annotationDao = database.annotationDao()
    private val layerDao = database.annotationLayerDao()

    // ── Layer operations ──────────────────────────────────────────────────────

    fun getLayersForFile(fileId: String): Flow<List<AnnotationLayer>> =
        layerDao.getLayersForFile(fileId).map { list -> list.map { it.toDomain() } }

    suspend fun getLayersForFileOnce(fileId: String): List<AnnotationLayer> =
        layerDao.getLayersForFileOnce(fileId).map { it.toDomain() }

    /**
     * Create a new layer for [fileId] owned by [ownerId].
     * Assigns a [colorIndex] cycling through the palette based on existing layer count.
     */
    suspend fun createLayer(
        fileId: String,
        name: String,
        ownerId: String,
        colorIndex: Int = 0,
        displayOrder: Int = 0
    ): AnnotationLayer {
        val entity = AnnotationLayerEntity(
            id = UUID.randomUUID().toString(),
            fileId = fileId,
            name = name,
            ownerId = ownerId,
            colorIndex = colorIndex,
            displayOrder = displayOrder,
            createdAt = System.currentTimeMillis()
        )
        layerDao.insertLayer(entity)
        return entity.toDomain()
    }

    suspend fun renameLayer(layerId: String, name: String) {
        val entity = layerDao.getLayerById(layerId) ?: return
        layerDao.updateLayer(entity.copy(name = name))
    }

    /** Delete a layer and all its annotations (strokes + points cascade via DAO). */
    suspend fun deleteLayer(layerId: String) {
        // Delete strokes+points for all annotations in this layer
        val annotations = annotationDao.getAnnotationsByLayersOnce(listOf(layerId))
        for (ann in annotations) {
            val strokes = annotationDao.getStrokesByAnnotation(ann.id)
            strokes.forEach { annotationDao.deletePointsByStroke(it.id) }
            annotationDao.deleteStrokesByAnnotation(ann.id)
        }
        annotationDao.deleteAnnotationsByLayer(layerId)
        layerDao.deleteLayerById(layerId)
    }

    // ── Annotation queries ────────────────────────────────────────────────────

    /** Live flow of all annotations for a set of layers. */
    fun getAnnotationsByLayers(layerIds: List<String>): Flow<List<Annotation>> {
        if (layerIds.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return annotationDao.getAnnotationsByLayers(layerIds).map { entities ->
            entities.map { it.toDomain(getStrokesForAnnotation(it.id)) }
        }
    }

    /** One-shot fetch of annotations for a set of layers. */
    suspend fun getAnnotationsByLayersOnce(layerIds: List<String>): List<Annotation> {
        if (layerIds.isEmpty()) return emptyList()
        return annotationDao.getAnnotationsByLayersOnce(layerIds).map {
            it.toDomain(getStrokesForAnnotation(it.id))
        }
    }

    /** Legacy: personal annotations for a member (used by FileItem properties dialog). */
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

    suspend fun getVisibleAnnotationsOnce(fileId: String, memberId: String, partIds: List<String>): List<Annotation> {
        return annotationDao.getVisibleAnnotationsOnce(fileId, memberId, partIds).map {
            it.toDomain(getStrokesForAnnotation(it.id))
        }
    }

    // ── Annotation CRUD ───────────────────────────────────────────────────────

    suspend fun createAnnotation(
        fileId: String,
        memberId: String,
        layerId: String,
        pageNumber: Int = 0,
        scope: AnnotationScope = AnnotationScope.PERSONAL,
        partId: String? = null
    ): Annotation {
        val now = System.currentTimeMillis()
        val entity = AnnotationEntity(
            id = UUID.randomUUID().toString(),
            fileId = fileId,
            memberId = memberId,
            layerId = layerId,
            pageNumber = pageNumber,
            scope = scope.name,
            partId = partId,
            createdAt = now,
            updatedAt = now
        )
        annotationDao.insertAnnotation(entity)
        return entity.toDomain(emptyList())
    }

    suspend fun saveAnnotationWithStrokes(annotation: Annotation) {
        // Delete stale strokes first, then insert current ones, then update the
        // annotation record last.  The updateAnnotation write is what triggers Room's
        // Flow observers, so strokes must already be in place before it fires.
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

        val entity = AnnotationEntity(
            id = annotation.id,
            fileId = annotation.fileId,
            memberId = annotation.memberId,
            layerId = annotation.layerId,
            pageNumber = annotation.pageNumber,
            scope = annotation.scope.name,
            partId = annotation.partId,
            createdAt = annotation.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        annotationDao.updateAnnotation(entity)
    }

    suspend fun deleteAnnotation(annotation: Annotation) {
        val entity = AnnotationEntity(
            id = annotation.id,
            fileId = annotation.fileId,
            memberId = annotation.memberId,
            layerId = annotation.layerId,
            pageNumber = annotation.pageNumber,
            scope = annotation.scope.name,
            partId = annotation.partId,
            createdAt = annotation.createdAt,
            updatedAt = annotation.updatedAt
        )
        annotationDao.deleteAnnotation(entity)
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
            AnnotationPointEntity(strokeId = stroke.id, x = point.x, y = point.y, pressure = point.pressure, timestamp = point.timestamp)
        }
        if (pointEntities.isNotEmpty()) annotationDao.insertPoints(pointEntities)
        return stroke
    }

    suspend fun deleteStroke(strokeId: String) {
        val stroke = annotationDao.getStrokeById(strokeId)
        if (stroke != null) annotationDao.deleteStroke(stroke)
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

    suspend fun clearAnnotationsForFile(fileId: String) {
        annotationDao.deleteAnnotationsByFile(fileId)
    }

    // ── Debug helpers ─────────────────────────────────────────────────────────

    suspend fun debugDumpForFile(fileId: String): String {
        val all = annotationDao.getAllAnnotationsForFile(fileId)
        if (all.isEmpty()) return "NO annotations found for fileId=$fileId"
        return buildString {
            appendLine("fileId queried: $fileId")
            appendLine("Total annotation rows: ${all.size}")
            all.forEach { ann ->
                val strokes = annotationDao.getStrokesByAnnotation(ann.id)
                appendLine("  • memberId=${ann.memberId}  layerId=${ann.layerId.take(12)}  page=${ann.pageNumber}  strokes=${strokes.size}  annId=${ann.id.take(8)}")
            }
        }
    }

    suspend fun debugDumpAll(): String {
        val all = annotationDao.getAllAnnotations()
        if (all.isEmpty()) return "DB is empty — no annotations at all"
        return buildString {
            appendLine("Total annotation rows in DB: ${all.size}")
            all.forEach { ann ->
                val strokes = annotationDao.getStrokesByAnnotation(ann.id)
                appendLine("  fileId=${ann.fileId.take(8)}  memberId=${ann.memberId}  layerId=${ann.layerId.take(12)}  page=${ann.pageNumber}  strokes=${strokes.size}")
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
    layerId = layerId,
    pageNumber = pageNumber,
    scope = try { AnnotationScope.valueOf(scope) } catch (e: Exception) { AnnotationScope.PERSONAL },
    partId = partId,
    strokes = strokes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun AnnotationLayerEntity.toDomain() = AnnotationLayer(
    id = id,
    fileId = fileId,
    name = name,
    ownerId = ownerId,
    colorIndex = colorIndex,
    displayOrder = displayOrder,
    createdAt = createdAt
)
