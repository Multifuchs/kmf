package de.mf.kmf.observer

import de.mf.kmf.core.KmfAttribute
import de.mf.kmf.core.KmfObject

sealed class KmfChangeEvent<T>(
    val property: KmfObservableProperty<out KmfChangeEvent<T>, T>
) {
    class Value<T>(property: KmfObservableProperty.Value<T>) :
        KmfChangeEvent<T>(property) {
    }

    class List<T>(property: KmfObservableProperty.List<T>) :
        KmfChangeEvent<T>(property) {

    }
}

interface KmfChangeListener<E : KmfChangeEvent<T>, T> {
    fun onChange(event: E)
}

sealed class KmfObservableProperty<E : KmfChangeEvent<T>, T>(
    val observedObject: KmfObject,
    val observedAttribute: KmfAttribute
) {

    private var listeners: MutableList<KmfChangeListener<E, T>>? = null

    fun addListener(l: KmfChangeListener<E, T>) {
        var list = listeners
        if (list == null) {
            list = mutableListOf()
            listeners = list
        }
        list += l
    }

    fun removeListener(l: KmfChangeListener<*, *>) {
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
        observedObject: KmfObject,
        observedAttribute: KmfAttribute.Unary
    ) : KmfObservableProperty<KmfChangeEvent.Value<T>, T>(
        observedObject,
        observedAttribute
    ) {

    }

    class List<T>(
        observedObject: KmfObject,
        observedAttribute: KmfAttribute.List
    ) : KmfObservableProperty<KmfChangeEvent.List<T>, T>(
        observedObject,
        observedAttribute
    ) {

    }
}