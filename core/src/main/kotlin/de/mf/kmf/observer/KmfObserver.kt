package de.mf.kmf.observer

import de.mf.kmf.core.KmfList
import de.mf.kmf.core.KmfObject

sealed class KmfChangeEvent<T>(
    val property: KmfObservableProperty<T>
) {

    class Resolved<T>(
        property: KmfObservableProperty<T>,
    ) : KmfChangeEvent<T>(property)

    class Unresolved<T>(
        property: KmfObservableProperty<T>
    ) : KmfChangeEvent<T>(property)

    class ValueChanged<T>(
        property: KmfObservableProperty.Value<T>,
        val oldValue: T,
        val newValue: T
    ) : KmfChangeEvent<T>(property)

    class ListSet<E: Any, T : KmfList<E>>(
        property: KmfObservableProperty.List<E>
    ) :
        KmfChangeEvent<KmfList<T>>(property) {
    }

    class ListMove<T : KmfList<*>>(
        property: KmfObservableProperty.List<T>
    ) :
        KmfChangeEvent<KmfList<T>>(property) {
    }

    class ListInsert<T : KmfList<*>>(
        property: KmfObservableProperty.List<T>
    ) :
        KmfChangeEvent<KmfList<T>>(property) {
    }

    class ListRemove<T : KmfList<*>>(
        property: KmfObservableProperty.List<T>
    ) :
        KmfChangeEvent<T>(property) {
    }
}

interface KmfChangeListener<T, P: KmfObservableProperty<T>> {
    fun onChange(event: KmfChangeEvent<T, P>)
}

sealed class KmfObservableProperty<T>(
    val root: KmfObject,
    val path: KmfAttrPath<T>
) {

    private var listeners: MutableList<KmfChangeListener<T, >>? = null

    fun addListener(l: KmfChangeListener<E>) {
        var list = listeners
        if (list == null) {
            list = mutableListOf()
            listeners = list
        }
        list += l
    }

    fun removeListener(l: KmfChangeListener<*>) {
        listeners?.remove(l)
        if (listeners?.isEmpty() == true) listeners = null
    }

    fun notify(e: E) {
        val list = listeners ?: return
        for (l in list) {
            l.onChange(e)
        }
    }

    class Value<T>(
        root: KmfObject,
        path: KmfUnaryAttrPath<T>
    ) : KmfObservableProperty<KmfChangeEvent<T>, T>(root, path) {

    }

    class List<T : Any>(
        root: KmfObject,
        path: KmfClosedListAttrPath<T>
    ) : KmfObservableProperty<KmfChangeEvent<KmfList<T>>, KmfList<T>>(
        root, path
    ) {
    }
}