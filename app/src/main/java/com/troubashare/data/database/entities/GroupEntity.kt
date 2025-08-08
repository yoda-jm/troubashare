package com.troubashare.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "members")
data class MemberEntity(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val name: String,
    val role: String? = null
)