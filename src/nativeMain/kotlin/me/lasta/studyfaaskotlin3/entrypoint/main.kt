package me.lasta.studyfaaskotlin3.entrypoint

import io.ktor.client.HttpClient
import io.ktor.client.engine.curl.Curl
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.util.KtorExperimentalAPI
import io.ktor.utils.io.core.use
import kotlinx.coroutines.runBlocking
import me.lasta.studyfaaskotlin3.entity.UserArticle
import me.lasta.studyfaaskotlin3.lambdaruntime.LambdaCustomRuntime
import me.lasta.studyfaaskotlin3.lambdaruntime.LambdaCustomRuntimeEnv

private const val URL = "https://jsonplaceholder.typicode.com/posts/1"

@KtorExperimentalAPI
fun main() {
    runBlocking {
        LambdaCustomRuntime().exec(fetchUserArticle)
    }
}

val fetchUserArticle: (LambdaCustomRuntimeEnv) -> UserArticle = { _ ->
    runBlocking {
        HttpClient(Curl) {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }.use { client ->
            println("request: $URL")
            client.get(URL)
        }
    }
}


