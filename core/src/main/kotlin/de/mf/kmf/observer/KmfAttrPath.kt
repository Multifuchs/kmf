package de.mf.kmf.observer

import de.mf.kmf.core.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf

sealed class KmfAttrPath<T> constructor(
    val root: KmfObject,
    val attrPath: List<KmfAttribute>
) {
    protected fun validate() {
        require(attrPath.isNotEmpty()) { "attrPath must not be empty." }
        for (i in 0..attrPath.lastIndex) {
            val attr = attrPath[i]
            if (i != attrPath.lastIndex) {
                require(attr.kind == KmfAttrKind.CHILD || attr.kind == KmfAttrKind.REFERENCE) {
                    "attrPath at index $i: ${attr.name} is not a child or reference attribute."
                }
            }
            val kmfClass =
                if (i == 0) root.kmfClass else (attrPath[i - 1].valueType as KClass<out KmfObject>).kmfClass
            require(kmfClass.allAttributes.contains(attr)) {
                "attrPath at index $i: ${attr.name} is not an attribute of $kmfClass."
            }

        }
    }
}

class KmfOpenUnaryAttrPath<T : KmfObject> internal constructor(
    root: KmfObject,
    attrPath: List<KmfAttribute>
) : KmfAttrPath<T>(root, attrPath) {
    init {
        validate()
        require(attrPath.last() is KmfAttribute.Unary) {
            "Last element in path must be a unary attribute."
        }
        val kind = attrPath.last().kind
        require(kind == KmfAttrKind.CHILD || kind == KmfAttrKind.REFERENCE) {
            "Last element in path must be a reference or child attribute."
        }
    }
}

class KmfClosedUnaryAttrPath<T> internal constructor(
    root: KmfObject,
    attrPath: List<KmfAttribute>
) : KmfAttrPath<T>(root, attrPath) {
    init {
        validate()
        require(attrPath.last() is KmfAttribute.Unary) {
            "Last element in path must be a unary attribute."
        }
    }
}

class KmfClosedListAttrPath<T : Any> internal constructor(
    root: KmfObject,
    attrPath: List<KmfAttribute>
) : KmfAttrPath<KmfList<T>>(root, attrPath) {
    init {
        validate()
        require(attrPath.last() is KmfAttribute.List) {
            "Last element in path must be a unary attribute."
        }
    }
}

fun KmfObject.attrPathToValue(
    beginningAttrs: List<KmfAttribute.Unary>,
    lastAttr: KmfAttribute.Unary
): KmfClosedUnaryAttrPath<Any?> =
    KmfClosedUnaryAttrPath(this, beginningAttrs + lastAttr)

fun KmfObject.attrPathToList(
    beginningAttrs: List<KmfAttribute.Unary>,
    lastAttr: KmfAttribute.List
): KmfClosedListAttrPath<Any> =
    KmfClosedListAttrPath(this, beginningAttrs + lastAttr)

infix fun <K : KmfObject, T : KmfObject> K.pathToKmfObj(
    prop: KMutableProperty1<K, T?>
): KmfOpenUnaryAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.Unary && attr.valueType == prop.returnType.classifier) {
        "Attribute $attr is not a unary reference or child attribute."
    }
    return KmfOpenUnaryAttrPath(
        this, listOf(attr)
    )
}

infix fun <K : KmfObject, T> K.pathToValue(
    prop: KMutableProperty1<K, T>
): KmfClosedUnaryAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.Unary && attr.valueType == prop.returnType.classifier) {
        "Attribute $attr is not a unary attribute."
    }
    return KmfClosedUnaryAttrPath(
        root,
        listOf(attr)
    )
}

infix fun <K : KmfObject, T : Any> K.pathToList(
    prop: KProperty1<K, KmfList<T>>
): KmfClosedListAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.List && attr.valueType == prop.returnType.arguments.first().type?.classifier) {
        "Attribute $attr is not a list attribute."
    }
    return KmfClosedListAttrPath(root, listOf(attr))
}

infix fun <P : KmfObject, T : KmfObject> KmfOpenUnaryAttrPath<P>.toKmfObj(
    prop: KMutableProperty1<P, T?>
): KmfOpenUnaryAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.Unary && attr.valueType == prop.returnType.classifier) {
        "Cannot extend path $this: attribute $attr is not a unary reference or child attribute."
    }
    return KmfOpenUnaryAttrPath(
        this.root, this.attrPath + attr
    )
}

infix fun <P : KmfObject, T> KmfOpenUnaryAttrPath<P>.toValue(
    prop: KMutableProperty1<P, T>
): KmfClosedUnaryAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.Unary && attr.valueType == prop.returnType.classifier) {
        "Cannot extend path $this: attribute $attr is not a unary attribute."
    }
    return KmfClosedUnaryAttrPath(
        this.root, this.attrPath + attr
    )
}

infix fun <P : KmfObject, T : Any> KmfOpenUnaryAttrPath<P>.toList(
    prop: KProperty1<P, T>
): KmfClosedListAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.List && attr.valueType == prop.returnType.arguments.first().type?.classifier) {
        "Cannot extend path $this: attribute $attr is not a list attribute."
    }
    return KmfClosedListAttrPath(
        this.root, this.attrPath + attr
    )
}

private fun findAttribute(obj: KmfObject, prop: KProperty<*>): KmfAttribute =
    requireNotNull(obj.kmfClass.allAttributes.firstOrNull { it.name == prop.name }) {
        "No attribute '${prop.name}' found in KmfClass ${obj.kmfClass}"
    }

private fun findAttribute(
    path: KmfOpenUnaryAttrPath<*>,
    prop: KProperty<*>
): KmfAttribute {
    val last = path.attrPath.last()
    require(last is KmfAttribute.Unary && last.valueType.isSubclassOf(KmfObject::class)) {
        "Can't create extend path $path with property ${prop.name}."
    }
    val nextKmfClass = (last.valueType as KClass<out KmfObject>).kmfClass
    return requireNotNull(nextKmfClass.allAttributes.firstOrNull { it.name == prop.name }) {
        "No attribute '${prop.name}' found in KmfClass $nextKmfClass to extend path $path."
    }
}