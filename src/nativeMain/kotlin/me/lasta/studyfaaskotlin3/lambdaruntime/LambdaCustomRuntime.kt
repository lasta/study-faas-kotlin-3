package me.lasta.studyfaaskotlin3.lambdaruntime

import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.core.use
import kotlinx.cinterop.toKString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.posix.getenv

class LambdaCustomRuntime {
    val httpClient
        get() = HttpClient(Curl) {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

    val lambdaRuntimeApi: String
        get() = requireNotNull(getenv("AWS_LAMBDA_RUNTIME_API")).toKString()

    val baseUrl: String
        get() = "http://$lambdaRuntimeApi/2018-06-01/runtime"

    @KtorExperimentalAPI
    suspend inline fun <reified T> exec(block: (LambdaCustomRuntimeEnv) -> T) {
        lateinit var lambdaEnv: LambdaCustomRuntimeEnv
        try {
            while (true) {
                lambdaEnv = initialize()

                val response = try {
                    block(lambdaEnv)
                } catch (e: Exception) {
                    e.printStackTrace()
                    sendInvocationError(lambdaEnv, e)
                    null
                } ?: continue

                sendResponse(lambdaEnv, response)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sendInitializeError(lambdaEnv, e)
        }
    }

    @KtorExperimentalAPI
    suspend inline fun initialize(): LambdaCustomRuntimeEnv = httpClient.use { client ->
        try {
            LambdaCustomRuntimeEnv(client.get("$baseUrl/invocation/next"))
        } catch (e: Exception) {
            TODO("do something from initialize")
        }
    }

    @KtorExperimentalAPI
    suspend inline fun sendInvocationError(
        lambdaEnv: LambdaCustomRuntimeEnv,
        error: Exception
    ): HttpResponse = httpClient.use { client ->
        try {
            client.post {
                url("http://$lambdaRuntimeApi/2018-06-01/runtime/invocation/${lambdaEnv.requestId}/error")
                body = TextContent(
                    Json.encodeToString(
                        mapOf(
                            "errorMessage" to error.toString(),
                            "errorType" to "InvocationError"
                        )
                    ),
                    contentType = ContentType.Application.Json
                )
            }
        } catch (e: Exception) {
            TODO("do something from send invocation error")
        }
    }

    @KtorExperimentalAPI
    suspend inline fun sendInitializeError(
        lambdaEnv: LambdaCustomRuntimeEnv,
        error: Exception
    ): HttpResponse = httpClient.use { client ->
        try {
            client.post {
                url("http://$lambdaRuntimeApi/2018-06-01/runtime/init/error")
                body = TextContent(
                    Json.encodeToString(
                        mapOf(
                            "errorMessage" to error.toString(),
                            "errorType" to "InvocationError"
                        )
                    ),
                    contentType = ContentType.Application.Json
                )
            }
        } catch (e: Exception) {
            TODO("do something from send initialize error")
        }
    }

    @KtorExperimentalAPI
    suspend inline fun <reified T> sendResponse(
        lambdaEnv: LambdaCustomRuntimeEnv,
        response: T
    ): HttpResponse = httpClient.use { client ->
        try {
            client.post {
                url("http://$lambdaRuntimeApi/2018-06-01/runtime/invocation/${lambdaEnv.requestId}/response")
                // FIXME: client serializes to Json by default content-type automatically but it raises ArrayIndexOutOfBoundsException
                body = TextContent(
                    Json.encodeToString(ResponseMessage(body = Json.encodeToString(response))),
                    contentType = ContentType.Application.Json
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            TODO("do something from send response")
        }
    }
}

