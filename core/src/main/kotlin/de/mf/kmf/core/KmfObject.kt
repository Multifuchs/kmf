package de.mf.kmf.core

abstract class KmfObject {

    abstract val kmfClass: KmfClass

    var parent: KmfObject? = null
        private set
    var parentChildAttribute: KmfAttribute? = null
        private set

    private var internalAdapterList: MutableList<KmfAdapter>? = null

    val adapters: List<KmfAdapter> get() = internalAdapterList ?: emptyList()

    fun <T : KmfAdapter> adapt(
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
        adapter.onAdapt(this)
        return adapter
    }

    fun removeAdapter(adapter: KmfAdapter) {
        internalAdapterList?.removeAll { it === adapter }
    }

    fun removeAdapter(type: Class<out KmfAdapter>) {
        internalAdapterList?.removeAll { it.javaClass === type }
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

    protected fun internalSetParent(
        newParent: KmfObject?,
        newAttribute: KmfAttribute?,
        listIndex: Int = -1
    ) {
        check((newParent == null) == (newAttribute == null))
        check(newAttribute == null || newAttribute.kind == KmfAttrKind.CHILD)

        if (this.parent === newParent && this.parentChildAttribute === newAttribute)
            return

        val oldParent = this.parent
        val oldAttr = this.parentChildAttribute

        if (newParent === this)
            throw KmfException(
                "Can't move this KmfObject to new parent, " +
                    "because new parent === this KmfObject (object can't be " +
                    "it's own parent)"
            )
        if (newParent?.seqToRoot()?.any { it === this } == true)
            throw KmfException(
                "Can't move this KmfObject to new parent, " +
                    "because the new parent is indirectly a child of this " +
                    "KmfObject (children-tree recursion)."
            )


        // update values
        this.parent = newParent
        this.parentChildAttribute = newAttribute

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
                            this,
                            null
                        )
                    )
                }
                is KmfAttribute.List ->
                    (oldAttr.getFrom(oldParent) as KmfChildrenListImpl<*>)
                        .removeWithoutParentNotify(this)
            }
        }
        if (newParent != null) {
            // add to new parent
            newAttribute!!
            when (newAttribute) {
                is KmfAttribute.Unary -> {
                    // check if we overwrite a child
                    val currentChild =
                        newAttribute.getFrom(newParent) as? KmfObject
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

                    newParent.internalSetValue(newAttribute, this)
                    newParent.notify(
                        KmfNotification.Set(
                            newParent,
                            newAttribute,
                            currentChild,
                            this
                        )
                    )
                }
                is KmfAttribute.List -> {
                    val list =
                        newAttribute.getFrom(newParent) as KmfChildrenListImpl<*>
                    list.addWithoutParentNotify(this, listIndex)
                }
            }
        }

        if (newParent !== oldParent) {
            notify(KmfNotification.Parent(this, oldParent, newParent))
        }
    }

    internal fun iinternalSetParent(
        parent: KmfObject?,
        attribute: KmfAttribute?,
        listIndex: Int = -1
    ) {
        internalSetParent(parent, attribute, listIndex)
    }

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

    override fun equals(other: Any?) = other === this

    override fun hashCode() = System.identityHashCode(this)

    override fun toString() = buildString {
        append(kmfClass.kClass.simpleName)
        append("[")
        for (attr in kmfClass.allAttributes) {
            append(attr.kProperty.name)
            append("=")
            when (attr.kind) {
                KmfAttrKind.PROPERTY ->
                    append(attr.getFrom(this@KmfObject))
                KmfAttrKind.REFERENCE -> TODO()
                KmfAttrKind.CHILD -> TODO()
            }
        }
        append("]")
    }
}