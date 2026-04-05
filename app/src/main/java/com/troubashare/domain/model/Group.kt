package com.troubashare.domain.model

enum class GroupType {
    BAND,     // No parts — each member manages files independently
    ENSEMBLE  // Parts defined (Soprano/Alto/Tenor/Bass, Guitar/Bass/Drums…)
}

data class Group(
    val id: String,
    val name: String,
    val type: GroupType = GroupType.BAND,
    val members: List<Member> = emptyList(),
    val parts: List<Part> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Member(
    val id: String,
    val groupId: String,
    val name: String,
    val partIds: List<String> = emptyList() // empty in Band mode
)

data class Part(
    val id: String,
    val groupId: String,
    val name: String,   // "Soprano", "Guitarist", "Drums"…
    val color: String? = null  // optional hex color
)
