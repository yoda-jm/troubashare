package com.troubashare.data.database.dao

import androidx.room.*
import com.troubashare.data.database.entities.AnnotationEntity
import com.troubashare.data.database.entities.AnnotationStrokeEntity
import com.troubashare.data.database.entities.AnnotationPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationDao {
    
    // Annotation CRUD
    @Query("SELECT * FROM annotations WHERE fileId = :fileId AND memberId = :memberId")
    fun getAnnotationsByFileAndMember(fileId: String, memberId: String): Flow<List<AnnotationEntity>>
    
    @Query("SELECT * FROM annotations WHERE fileId = :fileId AND memberId = :memberId AND pageNumber = :pageNumber")
    suspend fun getAnnotationsByFileAndMemberAndPage(fileId: String, memberId: String, pageNumber: Int): List<AnnotationEntity>
    
    @Query("SELECT * FROM annotations WHERE id = :annotationId")
    suspend fun getAnnotationById(annotationId: String): AnnotationEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: AnnotationEntity): Long
    
    @Update
    suspend fun updateAnnotation(annotation: AnnotationEntity)
    
    @Delete
    suspend fun deleteAnnotation(annotation: AnnotationEntity)
    
    @Query("DELETE FROM annotations WHERE fileId = :fileId")
    suspend fun deleteAnnotationsByFile(fileId: String)
    
    // Stroke CRUD
    @Query("SELECT * FROM annotation_strokes WHERE annotationId = :annotationId ORDER BY createdAt")
    suspend fun getStrokesByAnnotation(annotationId: String): List<AnnotationStrokeEntity>
    
    @Query("SELECT * FROM annotation_strokes WHERE id = :strokeId")
    suspend fun getStrokeById(strokeId: String): AnnotationStrokeEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStroke(stroke: AnnotationStrokeEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrokes(strokes: List<AnnotationStrokeEntity>)
    
    @Update
    suspend fun updateStroke(stroke: AnnotationStrokeEntity)
    
    @Delete
    suspend fun deleteStroke(stroke: AnnotationStrokeEntity)
    
    @Query("DELETE FROM annotation_strokes WHERE annotationId = :annotationId")
    suspend fun deleteStrokesByAnnotation(annotationId: String)
    
    // Point CRUD
    @Query("SELECT * FROM annotation_points WHERE strokeId = :strokeId ORDER BY timestamp")
    suspend fun getPointsByStroke(strokeId: String): List<AnnotationPointEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: AnnotationPointEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<AnnotationPointEntity>)
    
    @Query("DELETE FROM annotation_points WHERE strokeId = :strokeId")
    suspend fun deletePointsByStroke(strokeId: String)
    
    // Complex queries for complete annotation retrieval
    @Transaction
    @Query("""
        SELECT a.*, 
               s.id as stroke_id, s.color, s.strokeWidth, s.tool, s.createdAt as stroke_created,
               p.x, p.y, p.pressure, p.timestamp as point_timestamp
        FROM annotations a
        LEFT JOIN annotation_strokes s ON a.id = s.annotationId
        LEFT JOIN annotation_points p ON s.id = p.strokeId
        WHERE a.fileId = :fileId AND a.memberId = :memberId AND a.pageNumber = :pageNumber
        ORDER BY s.createdAt, p.timestamp
    """)
    suspend fun getCompleteAnnotationsForPage(fileId: String, memberId: String, pageNumber: Int): List<CompleteAnnotationData>
}