package de.mf.kmf.core

import java.util.*
import kotlin.reflect.full.primaryConstructor

abstract class AbstractKotlinDeserializer {

    protected var resolver: KmfResolver? = null

    private val unresolvedRefs = mutableListOf<UnresolvedRef>()
    private val stack = LinkedList<StackElement>()

    protected fun reset() {
        unresolvedRefs.clear()
        stack.clear()
    }

    protected fun startObject(clazz: KmfClass): KmfObject {
        val instance = clazz.kClass.primaryConstructor!!.call()

        // Add instance to its parent, which is the current head.
        val head = stack.peekLast()
        if (head != null) {
            unsafeAddValueToHead(instance)
        }

        // instance become the new head.
        stack.addLast(StackElement(instance))

        return instance
    }

    protected fun endObject(): KmfObject {
        check(stack.isNotEmpty()) {
            "endObject is called without previous corresponding startObject."
        }

        val removed = stack.removeLast()

        if (stack.isEmpty()) {
            // Root object was fully deserialized.
            // As the last step we must resolve unresolved references,
            // because now, we know all objects.
            resolveUnresolved(removed.obj)
            reset()
        }

        return removed.obj
    }

    protected fun startAttribute(name: String): KmfAttribute {
        val head = stack.peekLast()
        checkNotNull(head) {
            "startAttribute called without active kmfObject."
        }
        check(head.curAttr == null) {
            "startAttribute called with an currently active attribute: ${head.obj.debugPath()}.${head.curAttr!!.name}"
        }

        head.curAttr = head.obj.kmfClass.allAttributes
            .firstOrNull { it.name == name }
            ?: throw IllegalStateException("${head.obj.debugPath()} (${head.obj.kmfClass.kClass.qualifiedName}) doesn't have the attribute $name .")
        head.curAttrMissingRefs = 0

        return head.curAttr!!
    }

    protected fun addSimpleValue(value: Any?) {
        unsafeAddValueToHead(value)
    }

    protected fun addReferenceValue(path: String) {
        val (head, attr) = getCurAttribute()
        val resolved = resolver?.resolve(path)
        if (resolved == null) {
            unresolvedRefs += UnresolvedRef(
                head.obj, attr,
                if (attr is KmfAttribute.List) attr.get(head.obj).size + head.curAttrMissingRefs
                else 0,
                path
            )
            head.curAttrMissingRefs++
        } else {
            attr.addOrSet(head.obj, resolved)
        }
    }

    protected fun endAttribute() {
        val head = stack.peekLast()
        checkNotNull(head) {
            "endAttribute called without active kmfObject."
        }
        checkNotNull(head.curAttr) {
            "endAttribute called without active attribute."
        }
        head.curAttr = null
        head.curAttrMissingRefs = 0
    }

    private fun getCurAttribute(): Pair<StackElement, KmfAttribute> {
        val head = checkNotNull(stack.peekLast()) {
            "Cannot add a value to an attribute, because there is no active object."
        }
        val attribute = checkNotNull(head.curAttr) {
            "Before adding a value to an attribute, startAttribute must be called."
        }
        return Pair(head, attribute)
    }

    private fun unsafeAddValueToHead(value: Any?) {
        val (head, attribute) = getCurAttribute()
        attribute.addOrSet(head.obj, value)
    }

    private fun resolveUnresolved(rootObj: KmfObject) {
        if (unresolvedRefs.isEmpty()) return

        val rootObjResolver = KmfResolver(listOf(rootObj))
        for (unresolved in unresolvedRefs) {
            val resolvedObj = rootObjResolver.resolve(unresolved.path)
            checkNotNull(resolvedObj) {
                "Cannot resolve object referenced by ${unresolved.obj.debugPath()}.${unresolved.attribute.name} with path ${unresolved.path} ."
            }

            when (unresolved.attribute) {
                is KmfAttribute.Unary -> unresolved.attribute.set(
                    unresolved.obj,
                    resolvedObj
                )
                is KmfAttribute.List -> {
                    val listValue = unresolved.attribute
                        .get(unresolved.obj)
                    listValue.add(unresolved.index, resolvedObj)
                }
            }
        }
    }

    private data class StackElement(
        val obj: KmfObject,
        var curAttr: KmfAttribute? = null,
        var curAttrMissingRefs: Int = 0
    )

    private data class UnresolvedRef(
        val obj: KmfObject,
        val attribute: KmfAttribute,
        val index: Int,
        val path: String
    )
}
