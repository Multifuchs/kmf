package de.mf.kmf.observer

import de.mf.kmf.core.KmfAttribute
import de.mf.kmf.core.KmfList
import de.mf.kmf.core.KmfObject
import kotlin.reflect.KProperty1

sealed class KmfValuePath<R : KmfObject, T>(
    val objPath: KmfObjPath<R, *>,
    open val valueAttr: KmfAttribute
) {
    protected fun validateAttrExists() {
        require(objPath.destKmfClass.allAttributes.contains(valueAttr)) {
            "Attribute $valueAttr is not an attribute of ${objPath.destKmfClass}."
        }
    }

    fun get(root: R): T? {
        val destKmfObj = objPath.resolve(root) ?: return null
        @Suppress("UNCHECKED_CAST")
        return valueAttr.get(destKmfObj) as T?
    }

    fun getKmfValue(root: R): KmfValue<T> {
        val destKmfObj = objPath.resolve(root)
            ?: return KmfUnresolvedValue()
        @Suppress("UNCHECKED_CAST")
        return KmfResolvedValue(valueAttr.get(destKmfObj) as T)
    }
}

class KmfUnaryValuePath<R : KmfObject, T>(
    kmfObjPath: KmfObjPath<R, *>,
    valueAttr: KmfAttribute.Unary,
) : KmfValuePath<R, T>(kmfObjPath, valueAttr) {

    override val valueAttr get() = super.valueAttr as KmfAttribute.Unary

    init {
        validateAttrExists()
    }

    fun set(root: R, value: T): Boolean {
        val destKmfObj = objPath.resolve(root) ?: return false
        valueAttr.set(destKmfObj, value)
        return true
    }
}

sealed class KmfValue<T> {
    abstract fun orNull(): T?
    abstract fun orElse(altValue: T): T
    abstract fun orCompute(op: () -> T): T
}

class KmfUnresolvedValue<T> : KmfValue<T>() {
    override fun orNull() = null
    override fun orElse(altValue: T) = altValue
    override fun orCompute(op: () -> T) = op()

    override fun hashCode(): Int = 0x1EADBEEF
    override fun equals(other: Any?) = other is KmfUnresolvedValue<*>
    override fun toString() = "KmfUnresolvedValue"
}

class KmfResolvedValue<T>(val value: T) : KmfValue<T>() {
    override fun orNull() = value
    override fun orElse(altValue: T) = value
    override fun orCompute(op: () -> T) = value

    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?) =
        other is KmfResolvedValue<*> && value == other.value

    override fun toString() = "KmfResolvedValue($value)"
}


class KmfListValuePath<R : KmfObject, T : Any>(
    kmfObjPath: KmfObjPath<R, *>,
    valueAttr: KmfAttribute.List
) : KmfValuePath<R, KmfList<T>>(kmfObjPath, valueAttr) {
    override val valueAttr get() = super.valueAttr as KmfAttribute.List

    init {
        validateAttrExists()
    }
}

infix fun <R : KmfObject, D : KmfObject, T> KmfObjPath<R, D>.value(
    property: KProperty1<D, T>
): KmfUnaryValuePath<R, T> {
    val attr =
        destKmfClass.allAttributes.firstOrNull { it.name == property.name }
    require(attr is KmfAttribute.Unary) {
        "For KmfUnaryValuePath, the attribute must be unary."
    }
    return KmfUnaryValuePath(this, attr)
}

infix fun <R : KmfObject, D : KmfObject, T : Any> KmfObjPath<R, D>.list(
    property: KProperty1<D, KmfList<T>>
): KmfListValuePath<R, T> {
    val attr =
        destKmfClass.allAttributes.firstOrNull { it.name == property.name }
    require(attr is KmfAttribute.List) {
        "For KmfListValuePath, the attribute must be of kind list."
    }
    return KmfListValuePath(this, attr)
}