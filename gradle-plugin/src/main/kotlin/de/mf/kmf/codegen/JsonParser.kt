package de.mf.kmf.codegen

import com.beust.klaxon.JsonBase
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import java.io.Reader

internal data class RootDesc(
    val packageName: String,
    val imports: Map<String, String>,
    val classes: List<ClassDesc>
)

internal data class ClassDesc(
    val name: String,
    val superClass: String?,
    val attributes: List<AttrDesc>
)

internal enum class AttrDescType { PROPERTY, REFERENCE, CHILD }
internal enum class AttrDescMultiplicity {
    /** 0..1 */
    ONE,

    /** 0..n */
    MANY
}

internal data class AttrDesc(
    val name: String,
    val type: AttrDescType,
    val valueType: String,
    val nullable: Boolean,
    val multiplicity: AttrDescMultiplicity,
    val defaultValue: Any?
)

internal fun parseJson(jsonInput: Reader): RootDesc {
    val parser = Klaxon()
    val json = parser.parseJsonObject(jsonInput)

    val packageName = json.string("package") ?: ""
    val imports =
        json.obj("imports")?.let { jsonObj ->
            jsonObj.keys.asSequence()
                .map { key ->
                    val value = jsonObj[key] as? String
                        ?: throw Exception("Failed to parse $.import entry with name: $key.")
                    key to value
                }.toMap()
        } ?: emptyMap()

    val classes: List<ClassDesc> = json["classes"]?.let { jsonObj ->
        if (jsonObj !is JsonObject)
            throw Exception("Value of $.classes must be a json-object.")
        jsonObj.map { (className, classJsonDesc) ->
            if (classJsonDesc !is JsonObject)
                throw Exception("Value of $.classes.$className must be a json-object.")
            var superClass: String? = null
            val attrList = mutableListOf<AttrDesc>()
            classJsonDesc.forEach { key, value ->
                if (key == "superClass") {
                    if (value !is String)
                        throw Exception("Value of $.classes.$className.superClass must be a string.")
                    superClass = value
                    return@forEach
                }

                attrList += try {
                    parseAttrDesc(key, value).also {
                        validateAttrDesc(it)
                    }
                } catch (e: Exception) {
                    throw Exception("Value of $.classes.$className.$key is invalid: $value\n${e.message}")
                }
            }
            ClassDesc(className, superClass, attrList)
        }
    } ?: emptyList()

    return RootDesc(packageName, imports, classes)
}

private fun parseAttrDesc(name: String, json: Any?): AttrDesc = when (json) {
    is String -> parseAttrDesc(name, json, null)
    is JsonObject -> {
        val defaultValue = json["default"]
        if (defaultValue is JsonBase)
            throw Exception("Value of property \"default\" must be a string, number or boolean")
        val typeDescStr = json["type"] as? String
            ?: throw Exception("missing property \"type\" or it is not a string")

        parseAttrDesc(name, typeDescStr, defaultValue)
    }
    else -> throw Exception("Unexpected value type. Only string and object ({}) are allowed.")
}

private val valueTypePattern =
    """(?<type>(ref )|(child ))?(?<valueType>[\w\.]+)((?<many>\[\])|(?<null>\?))?""".toRegex()

private fun parseAttrDesc(
    name: String,
    s: String,
    defaultValue: Any?
): AttrDesc {
    val result = valueTypePattern.matchEntire(s)
        ?: throw Exception(
            "Value type doesn't match the required pattern: " +
                "\"(''|'ref'|'child')TYPENAME(''|'[]'|'?')\" " +
                "(examples: String, String?, String[], ref " +
                "org.example.OtherObj, ref org.example.OtherObj[], " +
                "child ObjSamePackage"
        )
    return AttrDesc(
        name,
        when (result.groups["type"]?.value?.trim()) {
            null -> AttrDescType.PROPERTY
            "ref" -> AttrDescType.REFERENCE
            "child" -> AttrDescType.CHILD
            else -> TODO("Add case to valueTypePattern")
        },
        result.groups["valueType"]!!.value.trim(),
        result.groups["null"] != null,
        if (result.groups["many"] != null) AttrDescMultiplicity.MANY
        else AttrDescMultiplicity.ONE,
        defaultValue
    )
}

private fun validateAttrDesc(ad: AttrDesc) {
    check(ad.name.isNotBlank()) {
        "Missing name"
    }
    check(ad.valueType.isNotBlank()) {
        "Missing value type"
    }
    check(ad.multiplicity != AttrDescMultiplicity.MANY || !ad.nullable) {
        "List attributes ([]) must not be nullable"
    }
    if (ad.name == "id") check(ad.multiplicity == AttrDescMultiplicity.ONE && ad.nullable && ad.valueType == "String") {
        "id property must always be of type String?"
    }
    when (ad.type) {
        AttrDescType.PROPERTY -> Unit
        AttrDescType.REFERENCE,
        AttrDescType.CHILD -> {
            val validMulNull = when (ad.multiplicity) {
                AttrDescMultiplicity.ONE -> ad.nullable
                AttrDescMultiplicity.MANY -> !ad.nullable
            }
            check(validMulNull) {
                "ref and child attributes must be either nullable (?) XOR list ([])"
            }
            check(ad.defaultValue == null) {
                "ref and child attributes must not have a default value"
            }
        }
    }
}