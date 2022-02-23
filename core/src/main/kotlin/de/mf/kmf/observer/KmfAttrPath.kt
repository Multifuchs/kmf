package de.mf.kmf.observer

import de.mf.kmf.core.*
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf

sealed class KmfAttrPath<T> constructor(
    val root: KmfClass,
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
                if (i == 0) root else (attrPath[i - 1].valueType as KClass<out KmfObject>).kmfClass
            require(kmfClass.allAttributes.contains(attr)) {
                "attrPath at index $i: ${attr.name} is not an attribute of $kmfClass."
            }
        }
    }

    fun getValue(rootObj: KmfObject): T? = resolveLastParent(rootObj)
        ?.let { attrPath.last().get(it) as T? }

    fun getOptionalValue(rootObj: KmfObject): Optional<T> =
        resolveLastParent(rootObj)?.let { Optional.of(it as T) }
            ?: Optional.empty()

    protected fun resolveLastParent(rootObj: KmfObject): KmfObject? {
        require(root.isSuperclassOf(rootObj.kmfClass)) {
            "rootObj is not compatible to path: path starts with a $root, but rootObj is a ${rootObj.kmfClass}."
        }

        var curObj = rootObj
        if (attrPath.size > 1) {
            for (i in 0 until attrPath.lastIndex) {
                curObj = attrPath[i].get(curObj) as KmfObject? ?: return null
            }
        }
        return curObj
    }
}

sealed class KmfUnaryAttrPath<T> constructor(
    root: KmfClass,
    attrPath: List<KmfAttribute>
) : KmfAttrPath<T>(root, attrPath) {
    init {
        validate()
        require(attrPath.last() is KmfAttribute.Unary) {
            "Last element in path must be a unary attribute."
        }
    }

    fun setValue(rootObj: KmfObject, newValue: T): Boolean {
        val lastParent = resolveLastParent(rootObj) ?: return false
        (attrPath.last() as KmfAttribute.Unary).set(lastParent, newValue)
        return true
    }
}

class KmfClosedUnaryAttrPath<T> constructor(
    root: KmfClass,
    attrPath: List<KmfAttribute>
) : KmfUnaryAttrPath<T>(root, attrPath)

class KmfOpenUnaryAttrPath<T : KmfObject> internal constructor(
    root: KmfClass,
    attrPath: List<KmfAttribute>
) : KmfUnaryAttrPath<T>(root, attrPath) {
    init {
        val kind = attrPath.last().kind
        require(kind == KmfAttrKind.CHILD || kind == KmfAttrKind.REFERENCE) {
            "Last element in path must be a reference or child attribute."
        }
    }
}

class KmfClosedListAttrPath<T : Any> internal constructor(
    root: KmfClass,
    attrPath: List<KmfAttribute>
) : KmfAttrPath<KmfList<T>>(root, attrPath) {
    init {
        validate()
        require(attrPath.last() is KmfAttribute.List) {
            "Last element in path must be a unary attribute."
        }
    }
}

fun KmfClass.attrPathToValue(
    beginningAttrs: List<KmfAttribute.Unary>,
    lastAttr: KmfAttribute.Unary
): KmfClosedUnaryAttrPath<Any?> =
    KmfClosedUnaryAttrPath(this, beginningAttrs + lastAttr)

fun KmfClass.attrPathToList(
    beginningAttrs: List<KmfAttribute.Unary>,
    lastAttr: KmfAttribute.List
): KmfClosedListAttrPath<Any> =
    KmfClosedListAttrPath(this, beginningAttrs + lastAttr)

infix fun <K : KmfObject, T : KmfObject> KClass<out K>.pathToKmfObj(
    prop: KMutableProperty1<K, T?>
): KmfOpenUnaryAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.Unary && attr.valueType == prop.returnType.classifier) {
        "Attribute $attr is not a unary reference or child attribute."
    }
    return KmfOpenUnaryAttrPath(
        this.kmfClass, listOf(attr)
    )
}

infix fun <K : KmfObject, T> KClass<out K>.pathToValue(
    prop: KMutableProperty1<K, T>
): KmfClosedUnaryAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.Unary && attr.valueType == prop.returnType.classifier) {
        "Attribute $attr is not a unary attribute."
    }
    return KmfClosedUnaryAttrPath(
        this.kmfClass,
        listOf(attr)
    )
}

infix fun <K : KmfObject, T : Any> KClass<out K>.pathToList(
    prop: KProperty1<K, KmfList<T>>
): KmfClosedListAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.List && attr.valueType == prop.returnType.arguments.first().type?.classifier) {
        "Attribute $attr is not a list attribute."
    }
    return KmfClosedListAttrPath(this.kmfClass, listOf(attr))
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
    prop: KProperty1<P, KmfList<T>>
): KmfClosedListAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.List && attr.valueType == prop.returnType.arguments.first().type?.classifier) {
        "Cannot extend path $this: attribute $attr is not a list attribute."
    }
    return KmfClosedListAttrPath(
        this.root, this.attrPath + attr
    )
}

private fun findAttribute(
    kClass: KClass<out KmfObject>,
    prop: KProperty<*>
): KmfAttribute =
    requireNotNull(kClass.kmfClass.allAttributes.firstOrNull { it.name == prop.name }) {
        "No attribute '${prop.name}' found in KmfClass ${kClass.kmfClass}"
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