package de.mf.kmf.core

internal class KmfChildrenListImpl<T : KmfObject>(
    owner: KmfObject,
    attr: KmfAttribute.List,
) : KmfListImpl<T>(
    owner,
    attr,
    true
) {

    override fun add(index: Int, element: T) {
        element.iinternalSetParent(owner, attr, index)
    }

    override fun removeAt(index: Int): T {
        val oldValue = wrapped[index]
        oldValue.iinternalSetParent(null, null, -1)
        return oldValue
    }

    override fun set(index: Int, element: T): T {
        val oldValue = wrapped[index]
        if (oldValue === element) return oldValue
        oldValue.iinternalSetParent(null, null)
        add(index, element)
        return oldValue
    }

    internal fun removeWithoutParentNotify(element: KmfObject) {
        val index = wrapped.indexOf(element)
        if (index == -1) throw KmfException("Can't remove child, which doesn't exist")
        wrapped.removeAt(index)
        owner.internalNotify(
            KmfNotification.ListRemove(
                owner,
                attr,
                index,
                element
            )
        )
    }

    internal fun addWithoutParentNotify(element: KmfObject, index: Int) {
        if (moveIfDistinct(index, element as T)) return
        wrapped.add(index, element as T)
        owner.internalNotify(
            KmfNotification.ListInsert(
                owner,
                attr,
                index,
                element
            )
        )
    }
}