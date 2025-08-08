package com.troubashare.domain.model

data class Group(
    val id: String,
    val name: String,
    val members: List<Member> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Member(
    val id: String,
    val name: String,
    val role: String? = null
)