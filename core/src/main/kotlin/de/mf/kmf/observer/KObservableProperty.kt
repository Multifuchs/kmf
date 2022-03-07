package de.mf.kmf.observer

import de.mf.kmf.core.*

sealed class KMFValueEvent<out T>(
    val observedKMFObj: ObservedKMFObj<*, out T>,
    val isResolved: Boolean
) {
    class Resolved<T>(
        observedKMFObj: ObservedKMFObj<*, out T>,
        val value: T,
        val notification: KmfNotification?
    ) :
        KMFValueEvent<T>(observedKMFObj, true) {

        override fun equals(other: Any?) = other is Resolved<*>
            && other.value == value && other.notification == notification
            && other.observedKMFObj === observedKMFObj
    }

    class Unresolved<T>(observedKMFObj: ObservedKMFObj<*, out T>) :
        KMFValueEvent<T>(observedKMFObj, false) {

        override fun equals(other: Any?) =
            other is Unresolved<*> && other.observedKMFObj === observedKMFObj
    }
}

typealias KMFValueListener<T> = (event: KMFValueEvent<T>) -> Unit

sealed class ObservedKMFObj<R : KmfObject, T>(
    val obj: R
) {

    internal val listeners = mutableListOf<KMFValueListener<T>>()

    var isActive = false
        private set

    abstract val path: KmfValuePath<R, T>

    private var rootAdapter: KObserverAdapter? = null

    fun addListener(l: KMFValueListener<T>) {
        if (listeners.none { it === l }) {
            listeners += l
            val kmfVal = getKmfValue()
            l(
                if (kmfVal is KmfResolvedValue) KMFValueEvent.Resolved(
                    this, kmfVal.value, null
                ) else KMFValueEvent.Unresolved(
                    this
                )
            )
        }
    }

    fun removeListener(l: KMFValueListener<T>) {
        if (listeners.removeIf { it === l }) {
            if (getKmfValue() is KmfResolvedValue) {
                l(KMFValueEvent.Unresolved(this))
            }
        }
    }

    open fun activate(): ObservedKMFObj<R, T> {
        if (!isActive) {
            if (rootAdapter == null) {
                rootAdapter = KObserverAdapter(this)
            }
            obj.adapt(rootAdapter!!)
            isActive = true
        }
        return this
    }

    open fun deactivate(): ObservedKMFObj<R, T> {
        if (isActive) {
            obj.removeAdapter(rootAdapter!!)
            isActive = false
        }
        return this
    }

    fun getValue(): T? = path.get(obj)

    fun getKmfValue(): KmfValue<T> = path.getKmfValue(obj)
}

class UnaryObservedKMFObj<R : KmfObject, T>(
    obj: R,
    override val path: KmfUnaryValuePath<R, T>,
    active: Boolean = false
) : ObservedKMFObj<R, T>(obj) {

    init {
        if (!active)
            activate()
    }

    fun setValue(value: T): Boolean = path.set(obj, value)
    override fun activate() = super.activate() as UnaryObservedKMFObj<R, T>
    override fun deactivate() = super.deactivate() as UnaryObservedKMFObj<R, T>
}

class ListObservedKMFObj<R : KmfObject, T : Any>(
    obj: R,
    override val path: KmfListValuePath<R, T>,
    active: Boolean = false
) : ObservedKMFObj<R, KmfList<T>>(obj) {

    init {
        if (active)
            activate()
    }

    override fun activate() = super.activate() as ListObservedKMFObj<R, T>
    override fun deactivate() = super.deactivate() as ListObservedKMFObj<R, T>
}

internal class KObserverAdapter(
    val obsKmfObj: ObservedKMFObj<*, *>,
    val index: Int = 0
) : KmfAdapter {

    // If is null, the value attribute of valuePath is observed.
    private val kmfObjAttr = obsKmfObj
        .path
        .objPath
        .attributes
        .getOrNull(index)

    private var curChild: KmfObject? = null
    private var curChildAdapter: KObserverAdapter? = null

    override fun notify(notification: KmfNotification) {
        if (kmfObjAttr != null) {
            // attribute points to next object in path
            if (notification.attribute === kmfObjAttr && notification is KmfNotification.Set) {
                adaptChild(notification.newValue as KmfObject?)
            }
        } else if (notification.attribute === obsKmfObj.path.valueAttr) {
            // value has been changed
            notify(
                KMFValueEvent.Resolved(
                    obsKmfObj,
                    if (notification is KmfNotification.Set) notification.newValue
                    else obsKmfObj.getValue(),
                    notification
                )
            )
        }
    }

    override fun onAttach(obj: KmfObject) {
        if (kmfObjAttr != null) {
            adaptChild(kmfObjAttr.get(obj) as KmfObject?)
        } else {
            notify(
                KMFValueEvent.Resolved(
                    obsKmfObj,
                    obsKmfObj.getValue(),
                    null
                )
            )
        }
    }

    override fun onDetach(obj: KmfObject) {
        if (kmfObjAttr != null) {
            adaptChild(null)
        } else {
            notify(
                KMFValueEvent.Unresolved(
                    obsKmfObj
                )
            )
        }
    }

    private fun adaptChild(newChild: KmfObject?) {
        if (curChild === newChild) return
        if (curChild != null) {
            curChild!!.removeAdapter(curChildAdapter!!)
            curChild = null
            curChildAdapter = null
        }
        if (newChild != null) {
            curChildAdapter = KObserverAdapter(obsKmfObj, index + 1)
            newChild.adapt(curChildAdapter!!)
            curChild = newChild
        }
    }

    private fun notify(event: KMFValueEvent<*>) {
        var firstErr: Exception? = null;
        for (l in obsKmfObj.listeners) {
            try {
                l(event as KMFValueEvent<Nothing>)
            } catch (e: Exception) {
                if (firstErr != null) firstErr = e
            }
        }
        if (firstErr != null) {
            throw KmfException(
                "Sending value change notification to at least one listener failed.",
                firstErr
            )
        }
    }
}