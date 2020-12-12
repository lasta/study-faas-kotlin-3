package me.lasta.studyfaaskotlin3.lambdaruntime

import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class LambdaCustomRuntimeEnv(
    private val response: HttpResponse
) {
    private val body: LambdaRuntimeRequestBody
        // TODO: change body type to data class
        get() = Json.decodeFromString(response.toString())

    val requestId: String
        get() = requireNotNull(
            response.headers["lambda-runtime-aws-request-id"],
            lazyMessage = {
                """
                "lambda-runtime-aws-request-id" must not be null
                """.trimIndent()
            }
        )

    // FIXME: define request value in LambdaRuntimeRequestBody and delete this function
    fun getRequestParameter(key: String) = body.parameters[key]
}
