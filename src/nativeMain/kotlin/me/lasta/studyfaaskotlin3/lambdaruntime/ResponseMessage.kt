package me.lasta.studyfaaskotlin3.lambdaruntime

import kotlinx.serialization.Serializable

@Serializable
data class ResponseMessage<T>(
    val statusCode: Int = 200,
    val body: T
)


