package de.mf.kmf.core

internal open class KmfListImpl<T : Any>(
    protected val owner: KmfObject,
    protected val attr: KmfAttribute.List,
    protected val distinct: Boolean
) : KmfList<T>, AbstractMutableList<T>() {

    protected val wrapped = mutableListOf<T>()

    override val size: Int get() = wrapped.size

    override fun add(index: Int, element: T) {
        if (moveIfDistinct(index, element)) return
        wrapped.add(index, element)
        owner.internalNotify(
            KmfNotification.ListInsert(
                owner,
                attr,
                index,
                element
            )
        )
    }

    override fun removeAt(index: Int): T {
        val oldVal = wrapped.removeAt(index)
        owner.internalNotify(
            KmfNotification.ListRemove(
                owner,
                attr,
                index,
                oldVal
            )
        )
        return oldVal
    }

    override fun set(index: Int, element: T): T {
        if (wrapped[index] === element) return element

        val oldVal = wrapped[index]
        if (moveIfDistinct(index, element))
            return oldVal
        wrapped[index] = element
        owner.internalNotify(
            KmfNotification.ListSet(owner, attr, index, oldVal, element)
        )
        return oldVal
    }

    override fun get(index: Int): T = wrapped[index]

    override fun move(from: Int, to: Int) {
        if (from == to) return
        val value = wrapped[from]
        wrapped.add(to, value)
        wrapped.removeAt(from)
        owner.internalNotify(
            KmfNotification.ListMove(owner, attr, from, to, value)
        )
    }

    protected fun moveIfDistinct(index: Int, element: T): Boolean {
        if (distinct) {
            val i = indexOf(element)
            if (i != -1) {
                if (i != index) move(i, index)
                return true
            }
        }
        return false
    }

}