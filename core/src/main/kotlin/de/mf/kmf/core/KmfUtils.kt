package de.mf.kmf.core

import java.util.*

fun KmfObject.seqToRoot() = object : Sequence<KmfObject> {
    override fun iterator(): Iterator<KmfObject> =
        object : Iterator<KmfObject> {
            var next: KmfObject? = this@seqToRoot

            override fun hasNext(): Boolean = next != null

            override fun next(): KmfObject {
                val result = next!!
                next = result.parent
                return result
            }
        }
}

fun KmfObject.root(): KmfObject {
    var cur = this
    while (true) {
        cur = cur.parent ?: break
    }
    return cur
}

fun KmfObject.idOrNull(): String? = kmfClass.id?.getFrom(this) as String?

/**
 * Transitively iterates over all children.
 */
fun KmfObject.childTree(predicate: (KmfObject) -> Boolean) {
    for (childAttr in kmfClass.allChildren) {
        when (childAttr) {
            is KmfAttribute.Unary -> {
                val child = childAttr.getFrom(this) as? KmfObject
                if (child != null && predicate(child))
                    child.childTree(predicate)
            }
            is KmfAttribute.List -> {
                val list = childAttr.getFrom(this)
                for (child in list) {
                    if (child is KmfObject && predicate(child))
                        child.childTree(predicate)
                }
            }
        }
    }
}