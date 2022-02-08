package de.mf.kmf.json

import de.mf.kmf.core.AbstractKmfSerializer
import de.mf.kmf.core.KmfAttribute
import de.mf.kmf.core.KmfObject
import de.mf.kmf.core.path
import java.io.BufferedWriter
import java.io.Writer
import javax.json.Json
import javax.json.stream.JsonGenerator
import kotlin.reflect.full.isSubclassOf

fun KmfObject.writeJson(
    writer: Writer,
    customSerializers: List<AbstractKmfSerializer.ValueSerializer<*>> = emptyList(),
    pretty: Boolean = true
) {
    KmfJsonSerializer(writer, customSerializers, pretty).serialize(this)
}

private class KmfJsonSerializer(
    val writer: Writer,
    val customSerializers: List<ValueSerializer<out Any>>,
    val pretty: Boolean
) : AbstractKmfSerializer() {

    private val jsonGen = Json.createGeneratorFactory(
        mapOf<String, Any>(
            JsonGenerator.PRETTY_PRINTING to pretty
        )
    ).createGenerator(writer)

    private val serializers = customSerializers + Serializer.all

    private var ignoreNextStartObject = false

    fun serialize(obj: KmfObject) {
        execSerialize(obj)
    }

    override fun startObject(
        obj: KmfObject,
        serializedParents: List<KmfObject>
    ) {
        if (!ignoreNextStartObject)
            jsonGen.writeStartObject()
        ignoreNextStartObject = false
    }

    override fun endObject(obj: KmfObject, serializedParents: List<KmfObject>) {
        jsonGen.writeEnd()
    }

    override fun onSimpleProperty(
        obj: KmfObject,
        prop: KmfAttribute,
        value: Any?,
        serializedParents: List<KmfObject>
    ) {
        val n = prop.name
        val pvt = prop.valueType
        if (value == null) {
            jsonGen.writeNull(n)
            return
        }
        when {
            pvt === String::class -> jsonGen.write(n, value as String)
            pvt === Boolean::class -> jsonGen.write(n, value as Boolean)
            pvt === Int::class -> jsonGen.write(n, value as Int)
            pvt === Long::class -> jsonGen.write(n, value as Long)
            pvt === Double::class -> jsonGen.write(n, value as Double)
            pvt.isSubclassOf(Enum::class) -> jsonGen.write(
                n, (value as Enum<*>).name
            )
            else -> {
                val serializer = serializers.firstOrNull {
                    it.valueType === pvt
                } as ValueSerializer<Any>?
                checkNotNull(serializer) { "Cannot serialize a value of type ${pvt}." }
                jsonGen.write(n, serializer.serialize(value))
            }
        }
    }

    override fun onSimpleListProperty(
        obj: KmfObject,
        prop: KmfAttribute,
        values: List<Any>,
        serializedParents: List<KmfObject>
    ) {
        val n = prop.name
        val pvt = prop.valueType
        val writeElemFun: (Any) -> Unit = when {
            pvt === String::class -> { v -> jsonGen.write(v as String) }
            pvt === Boolean::class -> { v -> jsonGen.write(v as Boolean) }
            pvt === Int::class -> { v -> jsonGen.write(v as Int) }
            pvt === Long::class -> { v -> jsonGen.write(v as Long) }
            pvt === Double::class -> { v -> jsonGen.write(v as Double) }
            pvt.isSubclassOf(Enum::class) -> { v -> jsonGen.write((v as Enum<*>).name) }
            else -> checkNotNull(serializers.firstOrNull {
                it.valueType === pvt
            } as ValueSerializer<Any>?) { "Cannot serialize a value of type ${pvt}." }
                .let { s -> { v: Any -> jsonGen.write(s.serialize(v)) } }
        }

        jsonGen.writeStartArray(n)
        for (v in values) writeElemFun(v)
        jsonGen.writeEnd()
    }

    override fun onReferenceProperty(
        obj: KmfObject,
        prop: KmfAttribute,
        refObj: KmfObject?,
        serializedParents: List<KmfObject>
    ) {
        if (refObj == null) jsonGen.writeNull(prop.name)
        else jsonGen.write(prop.name, refObj.path())
    }

    override fun onReferenceListProperty(
        obj: KmfObject,
        prop: KmfAttribute,
        refObjList: List<KmfObject>,
        serializedParents: List<KmfObject>
    ) {
        jsonGen.writeStartArray(prop.name)
        for (refObj in refObjList) {
            jsonGen.write(refObj.path())
        }
        jsonGen.writeEnd()
    }

    override fun startChildAttribute(
        obj: KmfObject,
        prop: KmfAttribute,
        serializedParents: List<KmfObject>
    ) {
        when (prop) {
            is KmfAttribute.Unary -> {
                jsonGen.writeStartObject(prop.name)
                ignoreNextStartObject = true
            }
            is KmfAttribute.List ->
                jsonGen.writeStartArray(prop.name)
        }
    }

    override fun endChildAttribute(
        obj: KmfObject,
        prop: KmfAttribute,
        serializedParents: List<KmfObject>
    ) {
        if (prop is KmfAttribute.List)
            jsonGen.writeEnd()
    }

    override fun finishSerialization() {
        jsonGen.close()
        (writer as? BufferedWriter)?.flush()
    }
}