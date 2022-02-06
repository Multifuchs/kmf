package de.mf.kmf.core

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

fun KmfObject.pathFromRoot(): List<KmfObject> {
    val r = mutableListOf<KmfObject>()
    var cur = this
    while (true) {
        r += cur
        cur = cur.parent ?: break
    }
    r.reverse()
    return r
}

fun KmfObject.debugPath(
    showOnlyProperties: Boolean = true,
    showIds: Boolean = true,
    forceFullyQualifiedClassnames: Boolean = false
): String = buildString {
    val path = this@debugPath.pathFromRoot()
    for (i in 0..path.lastIndex) {
        val cur = path[i]
        val next = path.getOrNull(i + 1)

        if (i == 0 || !showOnlyProperties) {
            append(
                if (
                    !forceFullyQualifiedClassnames
                    && cur.javaClass.packageName == this@debugPath.javaClass.packageName
                ) cur.javaClass.simpleName
                else cur.javaClass.canonicalName
            )
        }
        if (showIds) {
            val id = cur.idOrNull()
            if (id != null) {
                append("<").append(id).append(">")
            }
        }

        if (next != null) {
            val attr = next.parentChildAttribute!!
            append(".").append(attr.kProperty.name)
            if (attr is KmfAttribute.List) {
                append("[")
                append(attr.getFrom(cur).indexOf(next))
                append("]")
            }

            if (!showOnlyProperties)
                append("/")
        }
    }
}

val KmfObject.root: KmfObject
    get() {
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