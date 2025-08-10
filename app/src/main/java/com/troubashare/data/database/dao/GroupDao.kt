package com.troubashare.data.database.dao

import androidx.room.*
import com.troubashare.data.database.entities.GroupEntity
import com.troubashare.data.database.entities.MemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY updatedAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>
    
    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getGroupById(id: String): GroupEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)
    
    @Update
    suspend fun updateGroup(group: GroupEntity)
    
    @Delete
    suspend fun deleteGroup(group: GroupEntity)
    
    @Query("SELECT * FROM members WHERE groupId = :groupId")
    suspend fun getMembersByGroupId(groupId: String): List<MemberEntity>
    
    @Query("SELECT * FROM members WHERE groupId = :groupId")
    fun getMembersByGroupIdFlow(groupId: String): Flow<List<MemberEntity>>
    
    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun getMemberById(id: String): MemberEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: MemberEntity)
    
    @Update
    suspend fun updateMember(member: MemberEntity)
    
    @Delete
    suspend fun deleteMember(member: MemberEntity)
    
    @Query("DELETE FROM members WHERE groupId = :groupId")
    suspend fun deleteMembersByGroupId(groupId: String)
}