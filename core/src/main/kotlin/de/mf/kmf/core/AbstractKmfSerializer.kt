package de.mf.kmf.core

import java.util.*
import kotlin.collections.AbstractList

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

    protected fun execSerialize(obj: KmfObject) {
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
                    val value = attr.getFrom(head.obj)
                    if (ignoreDefaultValues && value == attr.defaultValue)
                        continue@attrLoop

                    try {
                        when (attr.kind) {
                            KmfAttrKind.PROPERTY -> when (attr) {
                                is KmfAttribute.Unary -> onSimpleProperty(
                                    head.obj, attr, attr.getFrom(head.obj),
                                    parentList
                                )
                                is KmfAttribute.List -> onSimpleListProperty(
                                    head.obj, attr,
                                    attr.getFrom(head.obj) as List<Any>,
                                    parentList
                                )
                            }
                            KmfAttrKind.REFERENCE -> when (attr) {
                                is KmfAttribute.Unary -> onReferenceProperty(
                                    head.obj, attr,
                                    attr.getFrom(head.obj) as KmfObject?,
                                    parentList
                                )
                                is KmfAttribute.List -> onReferenceListProperty(
                                    head.obj, attr,
                                    attr.getFrom(head.obj) as List<KmfObject>,
                                    parentList
                                )
                            }
                            else -> Unit
                        }
                    } catch (e: Exception) {
                        throw KmfException(
                            "Failed to serialize ${head.obj.debugPath()}.${attr.kProperty.name}",
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
                    val child = childAttr.getFrom(head.obj)
                    if (child == null) emptyList()
                    else listOf(child as KmfObject)
                }
                is KmfAttribute.List -> childAttr.getFrom(head.obj) as List<KmfObject>
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
    }

}