package me.lasta.sample.serialization

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class Project(val name: String, val language: String)

class SerializationSample {

    @Test
    fun serialize() {
        val actual = Json.encodeToString(
            Project("kotlinx.serialization", "Kotlin")
        )
        assertEquals(
            expected = """{"name":"kotlinx.serialization","language":"Kotlin"}""",
            actual = actual
        )
    }

    @Test
    fun deserialize() {
        val actual = Json.decodeFromString<Project>(
            """{"name":"kotlinx.serialization","language":"Kotlin"}"""
        )
        assertEquals(
            expected = Project("kotlinx.serialization", "Kotlin"),
            actual = actual
        )
    }
}
