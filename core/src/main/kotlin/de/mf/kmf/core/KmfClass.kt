package de.mf.kmf.core

import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

/** Contains reflective information about [KmfObject]. */
abstract class KmfClass(
    /** Kotlin-class of that object. */
    val kClass: KClass<out KmfObject>,
    /** [KmfClass] this class derives from. */
    val superClass: KmfClass?
) {

    private val ownAttributes = mutableListOf<KmfAttribute>()

    /** All attributes available. This include all derived attributes as well. */
    val allAttributes: List<KmfAttribute> by lazy {
        if (superClass != null) superClass.allAttributes + ownAttributes
        else ownAttributes
    }

    val allProperties: List<KmfAttribute> by lazy {
        allAttributes.filter { it.kind === KmfAttrKind.PROPERTY }
    }

    val allReferences: List<KmfAttribute> by lazy {
        allAttributes.filter { it.kind === KmfAttrKind.REFERENCE }
    }

    val allChildren: List<KmfAttribute> by lazy {
        allAttributes.filter { it.kind === KmfAttrKind.CHILD }
    }

    protected fun addAttribute(a: KmfAttribute) {
        ownAttributes += a
        a.owner = this
    }

    override fun equals(other: Any?) = this === other

    override fun hashCode() = System.identityHashCode(this)

    override fun toString(): String {
        return kClass.qualifiedName ?: kClass.toString()
    }
}

enum class KmfAttrKind {
    /** Simple primitive value. */
    PROPERTY,

    /** Reference to another [KmfObject]. */
    REFERENCE,

    /**
     * Child [KmfObject]. Each [KmfObject] can have only one parent:
     * when adding a child to another parent, it automatically will be removed
     * from its previous parent.
     */
    CHILD
}

sealed class KmfAttribute(
    val kmfClass: KmfClass,
    /**
     * The type of property values.
     * For [KmfAttribute.List], its the type of the elements.
     */
    val valueType: KClass<*>,
    val kind: KmfAttrKind,
    /**
     * The kotlin reflection property.
     */
    kProperty: KProperty1<*, *>
) {
    open val kProperty: KProperty1<in KmfObject, Any?> =
        kProperty as KProperty1<in KmfObject, Any?>

    lateinit var owner: KmfClass
        internal set

    open fun getFrom(obj: KmfObject): Any? {
        if (!owner.kClass.isInstance(obj))
            throw KmfException("The given object ")
        return kProperty.get(obj)
    }

    override fun equals(other: Any?) = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun toString() = "$owner::${kProperty.name}"

    /** 0..1 */
    class Unary(
        kmfClass: KmfClass,
        valueType: KClass<*>,
        kind: KmfAttrKind,
        val nullable: Boolean,
        kProperty: KProperty1<*, *>
    ) : KmfAttribute(kmfClass, valueType, kind, kProperty) {

        override val kProperty: KMutableProperty1<in KmfObject, Any?> =
            super.kProperty as KMutableProperty1<in KmfObject, Any?>

        /**
         * @param obj
         * @param value
         * @throws KmfException if the given
         */
        @Throws(KmfException::class)
        fun setAt(obj: KmfObject, value: Any?): Any? {
            if (value == null) {
                if (!nullable) throw KmfException("Failed to set value null, because $this isn't nullable.")
                kProperty.set(obj, null)
            }
            if (!valueType.isInstance(value))
                throw KmfException("Failed to set value, because $this expects the type $valueType.")
            val old = kProperty.get(obj)
            kProperty.set(obj, value)
            return old
        }
    }

    /** 0..n */
    class List(
        kmfClass: KmfClass,
        valueType: KClass<*>,
        kind: KmfAttrKind,
        kProperty: KProperty1<*, *>
    ) : KmfAttribute(kmfClass, valueType, kind, kProperty) {
        override val kProperty: KProperty1<in KmfObject, KmfList<Any>>
            get() = super.kProperty as KProperty1<in KmfObject, KmfList<Any>>

        override fun getFrom(obj: KmfObject): KmfList<Any> {
            return kProperty.get(obj)
        }
    }
}