package de.mf.kmf.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.AbstractList
import kotlin.reflect.KClass

abstract class AbstractKmfSerializer {

    /**
     * If `true`, [onSimpleProperty] and [onReferenceProperty] is only called,
     * if the property has a value other than the default value. For properties
     * with list values, an empty list is considered the default value.
     */
    protected var ignoreDefaultValues: Boolean = true

    protected open fun startObject(
        obj: KmfObject,
        serializedParents: List<KmfObject>
    ) = Unit

    protected open fun endObject(
        obj: KmfObject,
        serializedParents: List<KmfObject>
    ) = Unit

    protected open fun onSimpleProperty(
        obj: KmfObject,
        prop: KmfAttribute,
        value: Any?,
        serializedParents: List<KmfObject>
    ) = Unit

    protected open fun onSimpleListProperty(
        obj: KmfObject,
        prop: KmfAttribute,
        values: List<Any>,
        serializedParents: List<KmfObject>
    ) = Unit

    protected open fun onReferenceProperty(
        obj: KmfObject,
        prop: KmfAttribute,
        refObj: KmfObject?,
        serializedParents: List<KmfObject>
    ) = Unit

    protected open fun onReferenceListProperty(
        obj: KmfObject,
        prop: KmfAttribute,
        refObjList: List<KmfObject>,
        serializedParents: List<KmfObject>
    ) = Unit

    protected open fun startChildAttribute(
        obj: KmfObject,
        prop: KmfAttribute,
        serializedParents: List<KmfObject>
    ) = Unit

    protected open fun endChildAttribute(
        obj: KmfObject,
        prop: KmfAttribute,
        serializedParents: List<KmfObject>
    ) = Unit

    protected open fun finishSerialization() = Unit

    protected fun execSerialize(obj: KmfObject) {
        requireNotNull(obj.idOrNull()) {
            "KmfObject to be serialized must have an id."
        }
        class SerObj(
            val obj: KmfObject
        ) {
            var serializationStarted: Boolean = false
            var curChildAttr: Int = 0
            var curChild: Int = 0
        }

        val stack = LinkedList<SerObj>()
        // List for argument serializedParents
        val parentList = object : AbstractList<KmfObject>() {
            override val size get() = stack.size - 1
            override fun get(index: Int) = stack[index].obj
        }

        stack.addLast(SerObj(obj))

        mainLoop@ while (stack.isNotEmpty()) {
            val head = stack.peekLast()!!

            // Serialize non-children.
            if (!head.serializationStarted) {
                startObject(head.obj, parentList)
                attrLoop@ for (attr in head.obj.kmfClass.allAttributes) {
                    if (attr.kind == KmfAttrKind.CHILD) continue@attrLoop
                    val value = attr.get(head.obj)

                    try {
                        when (attr.kind) {
                            KmfAttrKind.PROPERTY -> when (attr) {
                                is KmfAttribute.Unary -> {
                                    val value = attr.get(head.obj)
                                    if (!ignoreDefaultValues || value != attr.defaultValue)
                                        onSimpleProperty(
                                            head.obj, attr, value,
                                            parentList
                                        )
                                }
                                is KmfAttribute.List -> {
                                    val list = attr.get(head.obj)
                                    if (!ignoreDefaultValues || list.isNotEmpty())
                                        onSimpleListProperty(
                                            head.obj, attr,
                                            list,
                                            parentList
                                        )
                                }
                            }
                            KmfAttrKind.REFERENCE -> when (attr) {
                                is KmfAttribute.Unary -> onReferenceProperty(
                                    head.obj, attr,
                                    attr.get(head.obj) as KmfObject?,
                                    parentList
                                )
                                is KmfAttribute.List -> onReferenceListProperty(
                                    head.obj, attr,
                                    attr.get(head.obj) as List<KmfObject>,
                                    parentList
                                )
                            }
                            else -> Unit
                        }
                    } catch (e: Exception) {
                        throw KmfException(
                            "Failed to serialize ${head.obj.debugPath()}.${attr.name}",
                            e
                        )
                    }
                }

                head.serializationStarted = true
            }

            // Serialize children.
            val childAttr =
                head.obj.kmfClass.allChildren.getOrNull(head.curChildAttr)
            if (childAttr == null) {
                // No more child attributes left. The current object is finished.
                endObject(head.obj, parentList)
                stack.removeLast()
                continue@mainLoop
            }

            val children = when (childAttr) {
                is KmfAttribute.Unary -> {
                    val child = childAttr.get(head.obj)
                    if (child == null) emptyList()
                    else listOf(child as KmfObject)
                }
                is KmfAttribute.List -> childAttr.get(head.obj) as List<KmfObject>
            }

            if (head.curChild == 0 && (!ignoreDefaultValues || children.isNotEmpty()))
                startChildAttribute(head.obj, childAttr, parentList)

            val nextChild = children.getOrNull(head.curChild)

            if (nextChild == null) {
                // No more children in property value. Goto next child-property.
                head.curChildAttr++
                head.curChild = 0

                if (!ignoreDefaultValues || children.isNotEmpty())
                    endChildAttribute(obj, childAttr, parentList)

                continue@mainLoop
            } else
                head.curChild++

            // Serialize next object.
            stack.addLast(SerObj(nextChild))
        }

        finishSerialization()
    }

    /** Converts a simple value into its string representation. */
    interface ValueSerializer<T : Any> {
        val valueType: KClass<T>
        fun serialize(value: T): String
        fun deserialize(string: String): T
    }

    object Serializer {

        val all = listOf(
            localDate, localDateTime, offsetDateTime, zonedDateTime
        )

        object localDate : ValueSerializer<LocalDate> {
            override val valueType = LocalDate::class

            override fun serialize(value: LocalDate) =
                value.format(DateTimeFormatter.ISO_LOCAL_DATE)

            override fun deserialize(string: String) = LocalDate.parse(
                string, DateTimeFormatter.ISO_LOCAL_DATE
            )
        }

        object localDateTime : ValueSerializer<LocalDateTime> {
            override val valueType = LocalDateTime::class
            override fun serialize(value: LocalDateTime) =
                value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

            override fun deserialize(string: String) = LocalDateTime.parse(
                string, DateTimeFormatter.ISO_LOCAL_DATE_TIME
            )
        }

        object offsetDateTime : ValueSerializer<OffsetDateTime> {
            override val valueType = OffsetDateTime::class

            override fun serialize(value: OffsetDateTime) =
                value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            override fun deserialize(string: String) = OffsetDateTime.parse(
                string, DateTimeFormatter.ISO_OFFSET_DATE_TIME
            )
        }

        object zonedDateTime : ValueSerializer<ZonedDateTime> {
            override val valueType = ZonedDateTime::class

            override fun serialize(value: ZonedDateTime) =
                value.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

            override fun deserialize(string: String) = ZonedDateTime.parse(
                string, DateTimeFormatter.ISO_ZONED_DATE_TIME
            )
        }

    }

}