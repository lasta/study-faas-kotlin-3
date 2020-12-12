package me.lasta.studyfaaskotlin3.entity

import kotlinx.serialization.Serializable

@Serializable
data class UserArticle(
    val userId: Int,
    val id: Int,
    val title: String,
    val body: String
)
