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
    class KmfOpenUnaryAttrPath<T : KmfObject>(
        root: KmfObject,
        attrPath: List<KmfAttribute>
    ) : KmfAttrPath<T>(root, attrPath)

    class KmfClosedUnaryAttrPath<T>(
        root: KmfObject,
        attrPath: List<KmfAttribute>
    ) : KmfAttrPath<T>(root, attrPath)

    class KmfClosedListAttrPath<T : Any>(
        root: KmfObject,
        attrPath: List<KmfAttribute>
    ) : KmfAttrPath<KmfList<T>>(root, attrPath)
}

infix fun <K : KmfObject, T : KmfObject> K.pathToKmfObj(
    prop: KMutableProperty1<K, T?>
): KmfAttrPath.KmfOpenUnaryAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.Unary && attr.valueType == prop.returnType.classifier) {
        "Attribute $attr is not a unary reference or child attribute."
    }
    return KmfAttrPath.KmfOpenUnaryAttrPath(
        this, listOf(attr)
    )
}

infix fun <K : KmfObject, T> K.pathToValue(
    prop: KMutableProperty1<K, T>
): KmfAttrPath.KmfClosedUnaryAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.Unary && attr.valueType == prop.returnType.classifier) {
        "Attribute $attr is not a unary attribute."
    }
    return KmfAttrPath.KmfClosedUnaryAttrPath(
        root,
        listOf(attr)
    )
}

infix fun <K : KmfObject, T : Any> K.pathToList(
    prop: KProperty1<K, KmfList<T>>
): KmfAttrPath.KmfClosedListAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.List && attr.valueType == prop.returnType.arguments.first().type?.classifier) {
        "Attribute $attr is not a list attribute."
    }
    return KmfAttrPath.KmfClosedListAttrPath(root, listOf(attr))
}

infix fun <P : KmfObject, T : KmfObject> KmfAttrPath.KmfOpenUnaryAttrPath<P>.toKmfObj(
    prop: KMutableProperty1<P, T?>
): KmfAttrPath.KmfOpenUnaryAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.Unary && attr.valueType == prop.returnType.classifier) {
        "Cannot extend path $this: attribute $attr is not a unary reference or child attribute."
    }
    return KmfAttrPath.KmfOpenUnaryAttrPath(
        this.root, this.attrPath + attr
    )
}

fun <P : KmfObject, T> toValue2(
    path: KmfAttrPath.KmfOpenUnaryAttrPath<P>,
    prop: KMutableProperty1<P, T>
): KmfAttrPath.KmfClosedUnaryAttrPath<T> {
    return path toValue prop
}

infix fun <P : KmfObject, T> KmfAttrPath.KmfOpenUnaryAttrPath<P>.toValue(
    prop: KMutableProperty1<P, T>
): KmfAttrPath.KmfClosedUnaryAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.Unary && attr.valueType == prop.returnType.classifier) {
        "Cannot extend path $this: attribute $attr is not a unary attribute."
    }
    return KmfAttrPath.KmfClosedUnaryAttrPath(
        this.root, this.attrPath + attr
    )
}

infix fun <P : KmfObject, T: Any> KmfAttrPath.KmfOpenUnaryAttrPath<P>.toList(
    prop: KProperty1<P, T>
): KmfAttrPath.KmfClosedListAttrPath<T> {
    val attr = findAttribute(this, prop)
    require(attr is KmfAttribute.List && attr.valueType == prop.returnType.arguments.first().type?.classifier) {
        "Cannot extend path $this: attribute $attr is not a list attribute."
    }
    return KmfAttrPath.KmfClosedListAttrPath(
        this.root, this.attrPath + attr
    )
}

private fun findAttribute(obj: KmfObject, prop: KProperty<*>): KmfAttribute =
    requireNotNull(obj.kmfClass.allAttributes.firstOrNull { it.name == prop.name }) {
        "No attribute '${prop.name}' found in KmfClass ${obj.kmfClass}"
    }

private fun findAttribute(
    path: KmfAttrPath.KmfOpenUnaryAttrPath<*>,
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