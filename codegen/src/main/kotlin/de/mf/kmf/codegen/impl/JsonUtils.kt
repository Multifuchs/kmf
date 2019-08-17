package de.mf.kmf.codegen.impl

import com.beust.klaxon.JsonObject
import java.lang.IllegalStateException

fun addPathFields(json: JsonObject, filename: String) {
    json["__path"] = "$filename:/"
    fun recursive(j: JsonObject) {
        val jPath = j.string("__path")!!
        for ((key, value) in j.entries) {
            if (value !is JsonObject) continue
            value["__path"] = "$jPath$key/"
            recursive(value)
        }
    }
    recursive(json)
}

fun JsonObject.requireString(key: String) =
    string(key)?.trim()?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException(
            "key '$key' not found in ${string("path")}"
        )

val JsonObject.path get() = string("__path")

val String.isJsonSpecialField get() = startsWith("__")