package de.mf.kmf.json

import de.mf.kmf.core.*
import java.io.Reader
import javax.json.Json
import javax.json.stream.JsonParser
import kotlin.reflect.KClass

fun <T : KmfObject> deserializeKmfJson(
    kClass: KClass<T>,
    resolver: KmfResolver,
    reader: Reader,
    customSerializers: List<AbstractKmfSerializer.ValueSerializer<*>> = emptyList()
): T {
    val serializers = customSerializers + AbstractKmfSerializer.Serializer.all
    val p = Json.createParser(reader)
    val t = KmfDeserializerTool()
    t.resolver = resolver
    var curClass = kClass.kmfClass
    var lastObj: KmfObject? = null
    var curAttr: KmfAttribute? = null
    var curSerializer: AbstractKmfSerializer.ValueSerializer<*>? = null

    while (p.hasNext()) {
        val e = p.next()!!
        when (e) {
            JsonParser.Event.START_OBJECT -> {
                t.startObject(curClass)
                curAttr = null
                curSerializer = null
            }
            JsonParser.Event.END_OBJECT -> {
                lastObj = t.endObject()
                curClass = lastObj.parent?.kmfClass ?: break
            }
            JsonParser.Event.KEY_NAME -> {
                check(curAttr == null) { "Starting new attribute ${p.string}, with $curAttr still being active." }
                curAttr = t.startAttribute(p.string)

                curSerializer = null
                when (curAttr.kind) {
                    KmfAttrKind.PROPERTY ->
                        curSerializer =
                            serializers.firstOrNull { it.valueType == curAttr!!.valueType }
                    KmfAttrKind.CHILD ->
                        curClass =
                            (curAttr.valueType as KClass<out KmfObject>).kmfClass
                }
            }
            JsonParser.Event.START_ARRAY -> {
                check(curAttr is KmfAttribute.List) { "Starting array for non-list attribute $curAttr." }
            }
            JsonParser.Event.END_ARRAY -> Unit
            JsonParser.Event.VALUE_STRING -> {
                if (curAttr == null)
                    throw IllegalStateException("Value without active attribute.")
                when (curAttr.kind) {
                    KmfAttrKind.PROPERTY -> {
                        if (curAttr.valueType == String::class)
                            t.addSimpleValue(p.string)
                        else if (curSerializer != null)
                            t.addSimpleValue(curSerializer.deserialize(p.string))
                        else
                            throw IllegalStateException("Can't deserialize values for attribute $curAttr.")
                    }
                    KmfAttrKind.REFERENCE -> t.addReferenceValue(p.string)
                    KmfAttrKind.CHILD -> throw IllegalStateException("Unexpected value for child attribute.")
                }
            }
            JsonParser.Event.VALUE_NUMBER -> {
                check(curAttr?.kind == KmfAttrKind.PROPERTY) { "Number-value for non-property attribute." }
                when (curAttr!!.valueType) {
                    Int::class -> t.addSimpleValue(p.int)
                    Long::class -> t.addSimpleValue(p.long)
                    Double::class -> t.addSimpleValue(p.bigDecimal.toDouble())
                    else -> throw IllegalStateException("Number-value for non-number property.")
                }
            }
            JsonParser.Event.VALUE_TRUE,
            JsonParser.Event.VALUE_FALSE -> {
                val value = e == JsonParser.Event.VALUE_TRUE
                check(curAttr!!.valueType == Boolean::class) { "Boolean-value for non-boolean property." }
                t.addSimpleValue(value)
            }
            JsonParser.Event.VALUE_NULL -> {
                t.addSimpleValue(null)
            }
        }

        if ((e == JsonParser.Event.END_OBJECT && curAttr is KmfAttribute.Unary && curAttr.kind == KmfAttrKind.CHILD)
            || e == JsonParser.Event.END_ARRAY
            || e.name.startsWith("VALUE_") && curAttr is KmfAttribute.Unary
        ) {
            curAttr = null
            curSerializer = null
        }
    }

    if (lastObj == null)
        throw IllegalStateException("Empty Json.")

    return lastObj as T
}