package de.mf.kmf.core

import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.isSubclassOf

/** Contains reflective information about [KmfObject]. */
abstract class KmfClass(
    /** Kotlin-class of that object. */
    val kClass: KClass<out KmfObject>,
    /** [KmfClass] this class derives from. */
    val superClass: KmfClass?
) {

    private val ownAttributes = mutableListOf<KmfAttribute>()

    open val id: KmfAttribute? = null

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

    fun isSuperclassOf(other: KmfClass): Boolean {
        var cur = other
        while (true) {
            if (cur === this) return true
            cur = cur.superClass ?: break
        }
        return false
    }

    fun isSubclassOf(other: KmfClass) = other.isSuperclassOf(this)

    override fun equals(other: Any?) = this === other

    override fun hashCode() = System.identityHashCode(this)

    override fun toString(): String {
        return kClass.qualifiedName ?: kClass.toString()
    }
}

val KClass<out KmfClass>.kmf
    get() = objectInstance!!

val KClass<out KmfObject>.kmfClass
    get() = nestedClasses
        .first { it.isSubclassOf(KmfClass::class) && it.objectInstance != null }
        .objectInstance as KmfClass

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

    abstract val defaultValue: Any?

    val name = kProperty.name
    val qualifiedName by lazy { "$owner::${name}" }

    lateinit var owner: KmfClass
        internal set

    fun isMemberOf(kmfClass: KmfClass) =
        this.kmfClass.isSuperclassOf(kmfClass)

    fun isMemberOf(obj: KmfObject) =
        isMemberOf(obj.kmfClass)

    abstract fun get(obj: KmfObject): Any?

    abstract fun getAsList(obj: KmfObject): kotlin.collections.List<Any>

    /** If the attribute is [Unary], the value is set. If it's [List], the value will be added. */
    abstract fun addOrSet(obj: KmfObject, value: Any?)

    /** Sets the attribute to its initial state. */
    abstract fun unset(obj: KmfObject)

    abstract fun isOfValueType(value: Any?): Boolean

    override fun equals(other: Any?) = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    override fun toString() = qualifiedName

    protected fun requireValueType(value: Any?) {
        require(isOfValueType(value)) {
            "Cannot assign ${value?.javaClass ?: "null"} to $this."
        }
    }

    protected fun requireObjType(obj: KmfObject) {
        require(isMemberOf(obj)) {
            "$this is not a member of ${obj.kmfClass}."
        }
    }

    /** 0..1 */
    class Unary(
        kmfClass: KmfClass,
        valueType: KClass<*>,
        kind: KmfAttrKind,
        override val defaultValue: Any?,
        val nullable: Boolean,
        kProperty: KProperty1<*, *>
    ) : KmfAttribute(kmfClass, valueType, kind, kProperty) {

        override val kProperty: KMutableProperty1<in KmfObject, Any?> =
            super.kProperty as KMutableProperty1<in KmfObject, Any?>

        fun set(obj: KmfObject, value: Any?) {
            requireObjType(obj)
            requireValueType(value)
            kProperty.set(obj, value)
        }

        override fun get(obj: KmfObject): Any? {
            requireObjType(obj)
            return kProperty.get(obj)
        }

        override fun getAsList(obj: KmfObject): kotlin.collections.List<Any> {
            val value = get(obj)
            return if (value == null) emptyList() else listOf(value)
        }

        override fun addOrSet(obj: KmfObject, value: Any?) {
            set(obj, value)
        }

        override fun unset(obj: KmfObject) {
            set(obj, if (nullable) null else defaultValue)
        }

        override fun isOfValueType(value: Any?) =
            (value == null && this.nullable)
                || (value != null && valueType.isInstance(value))
    }

    /** 0..n */
    class List(
        kmfClass: KmfClass,
        valueType: KClass<*>,
        kind: KmfAttrKind,
        kProperty: KProperty1<*, *>
    ) : KmfAttribute(kmfClass, valueType, kind, kProperty) {
        override val defaultValue = emptyList<Any>()

        override val kProperty: KProperty1<in KmfObject, KmfList<Any>>
            get() = super.kProperty as KProperty1<in KmfObject, KmfList<Any>>

        override fun get(obj: KmfObject): KmfList<Any> {
            requireObjType(obj)
            return kProperty.get(obj)
        }

        override fun getAsList(obj: KmfObject): kotlin.collections.List<Any> {
            return get(obj)
        }

        override fun addOrSet(obj: KmfObject, value: Any?) {
            requireValueType(value)
            get(obj).add(value!!)
        }

        override fun unset(obj: KmfObject) {
            get(obj).clear()
        }

        override fun isOfValueType(value: Any?) =
            value != null && valueType.isInstance(value)
    }
}