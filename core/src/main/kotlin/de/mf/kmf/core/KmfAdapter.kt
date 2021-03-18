package de.mf.kmf.core

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
}

sealed class KmfNotification(
    /** The object, whose state altered. */
    val obj: KmfObject
) {
    /** The attribute of [obj], whose state altered. */
    abstract val attribute: KmfAttribute?

    /** New value set for an `0..1` attribute. */
    class Set(
        obj: KmfObject,
        override val attribute: KmfAttribute.Unary,
        val oldValue: Any?,
        val newValue: Any?
    ) : KmfNotification(obj)

    /** A value set withing a `0..n` attribute list. */
    class ListSet(
        obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val index: Int,
        val oldValue: Any,
        val newValue: Any
    ) : KmfNotification(obj)

    /** A value was moved within the same list. */
    class ListMove(
        obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val fromIndex: Int,
        val toIndex: Int,
        val value: Any
    ) : KmfNotification(obj)

    /** A value was inserted into a list. */
    class ListInsert(
        obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val index: Int,
        val value: Any
    ) : KmfNotification(obj)

    /** A value was removed from a list. */
    class ListRemove(
        obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val index: Int,
        val oldValue: Any
    ) : KmfNotification(obj)

    /** The parent of an object changed. */
    class Parent(
        obj: KmfObject,
        val oldParent: KmfObject?,
        val newParent: KmfObject?
    ) : KmfNotification(obj) {
        override val attribute: KmfAttribute? = null
    }
}