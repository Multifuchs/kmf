package de.mf.kmf.core


interface KmfAdapter {
    fun notify(notification: KmfNotification)
}

sealed class KmfNotification(
    val obj: KmfObject
) {
    abstract val attribute: KmfAttribute?

    class Set(
        obj: KmfObject,
        override val attribute: KmfAttribute.Unary,
        val oldValue: Any?,
        val newValue: Any?
    ) : KmfNotification(obj)

    class ListSet(
        obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val index: Int,
        val oldValue: Any,
        val newValue: Any
    ) : KmfNotification(obj)

    class ListMove(
        obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val fromIndex: Int,
        val toIndex: Int,
        val value: Any
    ) : KmfNotification(obj)

    class ListInsert(
        obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val index: Int,
        val value: Any
    ) : KmfNotification(obj)

    class ListRemove(
        obj: KmfObject,
        override val attribute: KmfAttribute.List,
        val index: Int,
        val oldValue: Any
    ) : KmfNotification(obj)

    class Parent(
        obj: KmfObject,
        val oldParent: KmfObject?,
        val newParent: KmfObject?
    ) : KmfNotification(obj) {
        override val attribute: KmfAttribute? = null
    }
}