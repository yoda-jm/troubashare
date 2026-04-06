package com.troubashare.data.database.dao

import androidx.room.*
import com.troubashare.data.database.entities.AnnotationLayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnnotationLayerDao {

    @Query("SELECT * FROM annotation_layers WHERE fileId = :fileId ORDER BY displayOrder, createdAt")
    fun getLayersForFile(fileId: String): Flow<List<AnnotationLayerEntity>>

    @Query("SELECT * FROM annotation_layers WHERE fileId = :fileId ORDER BY displayOrder, createdAt")
    suspend fun getLayersForFileOnce(fileId: String): List<AnnotationLayerEntity>

    @Query("SELECT * FROM annotation_layers WHERE id = :layerId")
    suspend fun getLayerById(layerId: String): AnnotationLayerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLayer(layer: AnnotationLayerEntity): Long

    @Update
    suspend fun updateLayer(layer: AnnotationLayerEntity)

    @Delete
    suspend fun deleteLayer(layer: AnnotationLayerEntity)

    @Query("DELETE FROM annotation_layers WHERE id = :layerId")
    suspend fun deleteLayerById(layerId: String)

    @Query("DELETE FROM annotation_layers WHERE fileId = :fileId")
    suspend fun deleteLayersForFile(fileId: String)

    @Query("UPDATE annotation_layers SET isPromoted = :promoted WHERE id = :layerId")
    suspend fun setPromoted(layerId: String, promoted: Boolean)
}
