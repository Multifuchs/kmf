package de.mf.kmf.core

import kotlin.reflect.KProperty

/**
 * Adapters can be attached and optionally receive [KmfNotification] from a
 * [KmfObject]. Notifications are sent for all events, which change the state of
 * the object.
 *
 * An adapter can be attached to multiple objects. On the other hand, it can be
 * attached to a certain object only once.
 */
interface KmfAdapter {
    /**
     * An notification is sent after the state of an adapted object
     * changed.
     */
    fun notify(notification: KmfNotification) = Unit

    /**
     * Called when the adapter is added to a [KmfObject].
     */
    fun onAdapt(obj: KmfObject) = Unit
}

sealed interface KmfNotification {
    /** The attribute of [obj], whose state altered. */
    abstract val attribute: KmfAttribute?

    /** The object, whose state altered. */
    val obj: KmfObject

    /** New value set for an `0..1` attribute. */
    data class Set(
        override val obj: KmfObject,
        override val attribute: KmfAttribute.Unary,
        val oldValue: Any?,
        val newValue: Any?
    ) : KmfNotification

    /** A value set withing a `0..n` attribute list. */
    data class ListSet(
        override val obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val index: Int,
        val oldValue: Any,
        val newValue: Any
    ) : KmfNotification

    /** A value was moved within the same list. */
    data class ListMove(
        override val obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val fromIndex: Int,
        val toIndex: Int,
        val value: Any
    ) : KmfNotification

    /** A value was inserted into a list. */
    data class ListInsert(
        override val obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val index: Int,
        val value: Any
    ) : KmfNotification

    /** A value was removed from a list. */
    data class ListRemove(
        override val obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val index: Int,
        val oldValue: Any
    ) : KmfNotification

    /** The parent of an object changed. */
    data class Parent(
        override val obj: KmfObject,
        val oldParent: KmfObject?,
        val newParent: KmfObject?
    ) : KmfNotification {
        override val attribute: KmfAttribute? = null
    }
}