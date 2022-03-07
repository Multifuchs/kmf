package de.mf.kmf.core

abstract class KmfObject {

    abstract val kmfClass: KmfClass

    var parent: KmfObject? = null
        private set
    var parentChildAttribute: KmfAttribute? = null
        private set

    private var internalAdapterList: MutableList<KmfAdapter>? = null

    val adapters: List<KmfAdapter> get() = internalAdapterList ?: emptyList()

    fun <T : KmfAdapter> adaptIfNotExists(
        type: Class<T>,
        builder: ((KmfObject) -> T)? = null
    ): T {
        var ial = internalAdapterList
        if (ial != null) {
            val existing = ial.firstOrNull { it.javaClass === type }
            if (existing != null) return existing as T
        } else {
            ial = mutableListOf()
            internalAdapterList = ial
        }

        val adapter = builder?.invoke(this)
            ?: type.constructors.firstOrNull { it.parameterCount == 0 }
                ?.newInstance()
                as? T
            ?: throw KmfException(
                "Can't create adapter, because no builder was specified " +
                    "and the adapter type doesn't have a constructor without " +
                    "parameters."
            )
        ial.add(adapter)
        adapter.onAttach(this)
        return adapter
    }

    fun adapt(adapter: KmfAdapter) {
        var ial = internalAdapterList
        if(ial == null) {
            ial = mutableListOf()
            internalAdapterList = ial
        }
        ial += adapter
        adapter.onAttach(this)
    }

    fun removeAdapter(adapter: KmfAdapter) {
        removeAdapter { it === adapter }
    }

    fun removeAdapter(type: Class<out KmfAdapter>) {
        removeAdapter { it.javaClass === type }
    }

    private fun removeAdapter(predicate: (KmfAdapter) -> Boolean) {
        val removed = mutableListOf<KmfAdapter>()
        internalAdapterList?.removeAll {
            val rm = predicate(it)
            if (rm) removed += it
            rm
        }
        removed.forEach { it.onDetach(this) }
    }

    inline fun <reified T : KmfAdapter> adapterOrNull(): T? {
        return adapters.firstOrNull { it.javaClass === T::class.java } as? T
    }

    protected fun canNotify() = internalAdapterList?.isNotEmpty() == true

    protected fun notify(notification: KmfNotification) {
        val iae = internalAdapterList ?: return
        for (a in iae) {
            try {
                a.notify(notification)
            } catch (e: Exception) {
                throw KmfException(
                    "Sending notification to adapter with type " +
                        "${a.javaClass} failed. Notification: $notification",
                    e
                )
            }
        }
    }

    protected open fun internalSetValue(
        attribute: KmfAttribute,
        value: Any?
    ) {
        throw KmfException("Attribute not found: $attribute")
    }

    internal open fun iinternalSetValue(
        attribute: KmfAttribute,
        value: Any?
    ) {
        internalSetValue(attribute, value)
    }

    internal fun internalNotify(notification: KmfNotification) =
        notify(notification)

    /** Allows sub-classes to create instances of internal type [KmfListImpl]. */
    protected fun <T : Any> createSimpleList(
        attribute: KmfAttribute.List
    ): KmfList<T> {
        return KmfListImpl(
            this,
            attribute,
            attribute.kind != KmfAttrKind.PROPERTY
        )
    }

    /** Allows sub-classes to create instances of internal type [KmfChildrenListImpl]. */
    protected fun <T : KmfObject> createChildrenList(
        attribute: KmfAttribute.List
    ): KmfList<T> {
        return KmfChildrenListImpl(this, attribute)
    }

    infix fun deepEquals(other: KmfObject?): Boolean {
        if (other?.kmfClass !== kmfClass)
            return false
        for (propAttr in kmfClass.allProperties) {
            if (propAttr.get(this) != propAttr.get(other))
                return false
        }
        for (refAttr in kmfClass.allReferences) {
            val (thisRefValues, otherRefValues) = when (refAttr) {
                is KmfAttribute.Unary ->
                    listOf(refAttr.get(this) as KmfObject?) to listOf(
                        refAttr.get(other) as KmfObject?
                    )
                is KmfAttribute.List ->
                    (refAttr.get(this) as List<KmfObject?>) to (refAttr.get(
                        other
                    ) as List<KmfObject?>)
            }
            if (thisRefValues.size != otherRefValues.size)
                return false
            for (i in 0..thisRefValues.lastIndex) {
                val trv = thisRefValues[i]
                val orv = otherRefValues[i]
                if ((trv == null) != (orv == null)) return false
                if (trv != null && orv != null) {
                    // There are two types of reference values:
                    // 1 Those which live in the same tree as the object referencing it.
                    // 2 Those which live in a foreign tree
                    val isTrfForeign = trv.root !== this.root
                    val isOrfForeign = orv.root !== other.root
                    if (isTrfForeign != isOrfForeign)
                        return false
                    if (isTrfForeign) {
                        if (trv !== orv)
                            return false
                    } else {
                        // Referenced values in the same tree as object referencing them.
                        // Both are considered equal if their location is the same within the tree.
                        var curT: KmfObject = trv
                        var curO: KmfObject = orv
                        while (true) {
                            if ((curT.parent == null) != (curO.parent == null))
                                return false
                            if (curT.parentChildAttribute !== curO.parentChildAttribute)
                                return false;
                            curT = curT.parent ?: break
                            curO = curO.parent!!
                        }
                    }
                }
            }
        }
        for (childAttr in kmfClass.allChildren) {
            val (thisChildValues, otherChildValues) = when (childAttr) {
                is KmfAttribute.Unary ->
                    listOf(childAttr.get(this) as KmfObject?) to listOf(
                        childAttr.get(other) as KmfObject?
                    )
                is KmfAttribute.List ->
                    (childAttr.get(this) as List<KmfObject?>) to (childAttr.get(
                        other
                    ) as List<KmfObject?>)
            }
            if (thisChildValues.size != otherChildValues.size)
                return false
            for (i in 0..thisChildValues.lastIndex) {
                val tcv = thisChildValues[i]
                val ocv = otherChildValues[i]
                if ((tcv == null) != (ocv == null))
                    return false
                if (tcv != null && !(tcv deepEquals ocv))
                    return false
            }
        }

        return true
    }

    override fun hashCode() = System.identityHashCode(this)

    override fun toString() = buildString {
        append(kmfClass.kClass.simpleName)
        append("[")
        for (attr in kmfClass.allAttributes) {
            append(attr.name)
            when (attr.kind) {
                KmfAttrKind.PROPERTY ->
                    append("=").append(attr.get(this@KmfObject))
                KmfAttrKind.REFERENCE,
                KmfAttrKind.CHILD -> {
                    val value = attr.get(this@KmfObject)
                    append(if (attr.kind == KmfAttrKind.REFERENCE) "->" else "=>")

                    when (attr) {
                        is KmfAttribute.Unary -> append((value as? KmfObject)?.idOrNull())
                        is KmfAttribute.List -> {
                            append("[")
                            ((value as? List<*>)?.asSequence()
                                ?: emptySequence())
                                .filterIsInstance<KmfObject>()
                                .map { it.idOrNull() }
                                .filterNotNull()
                                .forEach { append(it).append(", ") }

                            append("]")
                        }
                    }
                }
            }
            append(", ")
        }
        append("]")
    }

    protected object ProtectedFunctions {
        fun setParent(
            child: KmfObject,
            newParent: KmfObject?,
            newAttribute: KmfAttribute?,
            listIndex: Int = -1
        ) {
            check((newParent == null) == (newAttribute == null))
            check(newAttribute == null || newAttribute.kind == KmfAttrKind.CHILD)

            if (child.parent === newParent && child.parentChildAttribute === newAttribute)
                return

            val oldParent = child.parent
            val oldAttr = child.parentChildAttribute

            if (newParent === child)
                throw KmfException(
                    "Can't move this KmfObject to new parent, " +
                        "because new parent === this KmfObject (object can't be " +
                        "it's own parent)"
                )
            if (newParent?.seqToRoot()?.any { it === child } == true)
                throw KmfException(
                    "Can't move this KmfObject to new parent, " +
                        "because the new parent is indirectly a child of this " +
                        "KmfObject (children-tree recursion)."
                )


            // update values
            child.parent = newParent
            child.parentChildAttribute = newAttribute

            if (oldParent != null) {
                oldAttr!!
                // remove from old parent
                when (oldAttr) {
                    is KmfAttribute.Unary -> {
                        oldParent.internalSetValue(oldAttr, null)
                        oldParent.notify(
                            KmfNotification.Set(
                                oldParent,
                                oldAttr,
                                child,
                                null
                            )
                        )
                    }
                    is KmfAttribute.List ->
                        (oldAttr.get(oldParent) as KmfChildrenListImpl<*>)
                            .removeWithoutParentNotify(child)
                }
            }
            if (newParent != null) {
                // add to new parent
                newAttribute!!
                when (newAttribute) {
                    is KmfAttribute.Unary -> {
                        // check if we overwrite a child
                        val currentChild =
                            newAttribute.get(newParent) as? KmfObject
                        if (currentChild != null) {
                            currentChild.parent = null
                            currentChild.parentChildAttribute = null
                            currentChild.notify(
                                KmfNotification.Parent(
                                    currentChild,
                                    newParent,
                                    null
                                )
                            )
                        }

                        newParent.internalSetValue(newAttribute, child)
                        newParent.notify(
                            KmfNotification.Set(
                                newParent,
                                newAttribute,
                                currentChild,
                                child
                            )
                        )
                    }
                    is KmfAttribute.List -> {
                        val list =
                            newAttribute.get(newParent) as KmfChildrenListImpl<*>
                        list.addWithoutParentNotify(child, listIndex)
                    }
                }
            }

            if (newParent !== oldParent) {
                child.notify(
                    KmfNotification.Parent(
                        child,
                        oldParent,
                        newParent
                    )
                )
            }
        }
    }

    internal object InternalFunctions {
        fun setParent(
            child: KmfObject,
            parent: KmfObject?,
            attribute: KmfAttribute?,
            listIndex: Int = -1
        ) {
            ProtectedFunctions.setParent(
                child,
                parent,
                attribute,
                listIndex
            )
        }
    }
}