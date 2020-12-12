package me.lasta.studyfaaskotlin3.lambdaruntime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LambdaRuntimeRequestBody(
    @SerialName("queryStringParameters")
    val parameters: Map<String, String>
)

