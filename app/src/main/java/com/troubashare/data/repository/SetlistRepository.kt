package com.troubashare.data.repository

import com.troubashare.data.database.TroubaShareDatabase
import com.troubashare.data.database.entities.SetlistEntity
import com.troubashare.data.database.entities.SetlistItemEntity
import com.troubashare.domain.model.Setlist
import com.troubashare.domain.model.SetlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

class SetlistRepository(
    private val database: TroubaShareDatabase,
    private val songRepository: SongRepository
) {
    
    private val setlistDao = database.setlistDao()
    
    fun getSetlistsByGroupId(groupId: String): Flow<List<Setlist>> {
        return setlistDao.getSetlistsByGroupId(groupId).map { entities ->
            entities.map { entity ->
                // For list view, don't load full items but show correct count
                val itemCount = setlistDao.getSetlistItemCount(entity.id)
                entity.toDomainModel(emptyList(), itemCount)
            }
        }
    }
    
    fun searchSetlists(groupId: String, query: String): Flow<List<Setlist>> {
        return setlistDao.searchSetlists(groupId, query).map { entities ->
            entities.map { entity ->
                // For search view, don't load full items but show correct count
                val itemCount = setlistDao.getSetlistItemCount(entity.id)
                entity.toDomainModel(emptyList(), itemCount)
            }
        }
    }
    
    suspend fun getSetlistById(id: String): Setlist? {
        val entity = setlistDao.getSetlistById(id) ?: return null
        val itemEntities = setlistDao.getSetlistItemsBySetlistId(id)
        
        val items = itemEntities.mapNotNull { itemEntity ->
            val song = songRepository.getSongById(itemEntity.songId)
            song?.let {
                SetlistItem(
                    id = itemEntity.id,
                    setlistId = itemEntity.setlistId,
                    song = it,
                    position = itemEntity.position,
                    key = itemEntity.key,
                    tempo = itemEntity.tempo,
                    notes = itemEntity.notes,
                    duration = itemEntity.duration
                )
            }
        }.sortedBy { it.position }
        
        return entity.toDomainModel(items)
    }
    
    suspend fun createSetlist(
        groupId: String,
        name: String,
        description: String? = null,
        venue: String? = null,
        eventDate: Long? = null
    ): Setlist {
        val setlistId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val entity = SetlistEntity(
            id = setlistId,
            groupId = groupId,
            name = name,
            description = description,
            venue = venue,
            eventDate = eventDate,
            createdAt = now,
            updatedAt = now
        )
        
        setlistDao.insertSetlist(entity)
        return entity.toDomainModel(emptyList(), 0)
    }
    
    suspend fun updateSetlist(setlist: Setlist): Setlist {
        val entity = SetlistEntity(
            id = setlist.id,
            groupId = setlist.groupId,
            name = setlist.name,
            description = setlist.description,
            venue = setlist.venue,
            eventDate = setlist.eventDate,
            createdAt = setlist.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        
        setlistDao.updateSetlist(entity)
        return setlist.copy(updatedAt = entity.updatedAt)
    }
    
    suspend fun deleteSetlist(setlist: Setlist) {
        val entity = SetlistEntity(
            id = setlist.id,
            groupId = setlist.groupId,
            name = setlist.name,
            description = setlist.description,
            venue = setlist.venue,
            eventDate = setlist.eventDate,
            createdAt = setlist.createdAt,
            updatedAt = setlist.updatedAt
        )
        
        // This will cascade delete all setlist items
        setlistDao.deleteSetlist(entity)
    }
    
    suspend fun addSongToSetlist(setlistId: String, songId: String, position: Int? = null): SetlistItem? {
        val song = songRepository.getSongById(songId) ?: return null
        
        val actualPosition = position ?: run {
            val existingItems = setlistDao.getSetlistItemsBySetlistId(setlistId)
            existingItems.size
        }
        
        val itemId = UUID.randomUUID().toString()
        val entity = SetlistItemEntity(
            id = itemId,
            setlistId = setlistId,
            songId = songId,
            position = actualPosition
        )
        
        setlistDao.insertSetlistItem(entity)
        return SetlistItem(
            id = itemId,
            setlistId = setlistId,
            song = song,
            position = actualPosition
        )
    }
    
    suspend fun removeSongFromSetlist(itemId: String) {
        val item = setlistDao.getSetlistItemById(itemId)
        if (item != null) {
            setlistDao.deleteSetlistItem(item)
            
            // Reorder remaining items to fill the gap
            val remainingItems = setlistDao.getSetlistItemsBySetlistId(item.setlistId)
                .filter { it.position > item.position }
                .map { it.copy(position = it.position - 1) }
            
            if (remainingItems.isNotEmpty()) {
                setlistDao.updateSetlistItems(remainingItems)
            }
        }
    }
    
    suspend fun reorderSetlistItems(setlistId: String, newOrder: List<String>) {
        val items = setlistDao.getSetlistItemsBySetlistId(setlistId)
        val reorderedItems = newOrder.mapIndexedNotNull { index, itemId ->
            items.find { it.id == itemId }?.copy(position = index)
        }
        
        setlistDao.updateSetlistItems(reorderedItems)
    }
    
    suspend fun updateSetlistItem(
        itemId: String,
        key: String? = null,
        tempo: Int? = null,
        notes: String? = null,
        duration: Int? = null
    ): SetlistItem? {
        val existingItem = setlistDao.getSetlistItemById(itemId) ?: return null
        val song = songRepository.getSongById(existingItem.songId) ?: return null
        
        val updatedEntity = existingItem.copy(
            key = key,
            tempo = tempo,
            notes = notes,
            duration = duration
        )
        
        setlistDao.updateSetlistItem(updatedEntity)
        
        return SetlistItem(
            id = updatedEntity.id,
            setlistId = updatedEntity.setlistId,
            song = song,
            position = updatedEntity.position,
            key = updatedEntity.key,
            tempo = updatedEntity.tempo,
            notes = updatedEntity.notes,
            duration = updatedEntity.duration
        )
    }
    
    suspend fun duplicateSetlist(setlistId: String, newName: String): Setlist? {
        val originalSetlist = getSetlistById(setlistId) ?: return null
        
        // Create new setlist
        val newSetlist = createSetlist(
            groupId = originalSetlist.groupId,
            name = newName,
            description = originalSetlist.description,
            venue = originalSetlist.venue,
            eventDate = originalSetlist.eventDate
        )
        
        // Copy all items
        val newItems = originalSetlist.items.map { originalItem ->
            SetlistItemEntity(
                id = UUID.randomUUID().toString(),
                setlistId = newSetlist.id,
                songId = originalItem.song.id,
                position = originalItem.position,
                key = originalItem.key,
                tempo = originalItem.tempo,
                notes = originalItem.notes,
                duration = originalItem.duration
            )
        }
        
        setlistDao.insertSetlistItems(newItems)
        
        // Return the complete new setlist
        return getSetlistById(newSetlist.id)
    }
}

// Extension functions for conversion
private fun SetlistEntity.toDomainModel(items: List<SetlistItem>, itemCount: Int? = null): Setlist {
    // Create dummy items for count display when items list is empty but count is provided
    val effectiveItems = if (items.isEmpty() && itemCount != null && itemCount > 0) {
        // Create placeholder items for count display only - these won't be used for detailed operations
        (1..itemCount).map { index ->
            SetlistItem(
                id = "placeholder-$index",
                setlistId = id,
                song = com.troubashare.domain.model.Song(
                    id = "placeholder-song",
                    groupId = groupId,
                    title = "Loading...",
                    artist = null,
                    key = null,
                    tempo = null,
                    tags = emptyList(),
                    notes = null,
                    files = emptyList()
                ),
                position = index - 1
            )
        }
    } else {
        items
    }
    
    return Setlist(
        id = id,
        groupId = groupId,
        name = name,
        description = description,
        venue = venue,
        eventDate = eventDate,
        items = effectiveItems,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}