package de.mf.kmf.util

import de.mf.kmf.core.*

interface ChildTreeListener {
    fun notify(adapter: ChildTreeAdapter, notification: KmfNotification) = Unit
    fun onChildAttached(adapter: ChildTreeAdapter, kmfObject: KmfObject) = Unit
    fun onChildDetached(adapter: ChildTreeAdapter, kmfObject: KmfObject) = Unit
}

class ChildTreeAdapter private constructor(
    val root: KmfObject
) : KmfAdapter {
    override fun notify(notification: KmfNotification) {

    }

    /** Appends this adapter to */
    private fun adaptObjAndSubTree(kmfObject: KmfObject) {
        kmfObject.iterateChildTree {
            if (kmfObject.adapterOrNull<ChildTreeAdapter>() != null)
                throw KmfException(
                    "${ChildTreeAdapter::class.simpleName} " +
                        "is can't be attached somewhere in a tree twice. Was attached "
                )
            true
        }
    }

    companion object {
        fun adapt(root: KmfObject): ChildTreeAdapter {
            val ca = ChildTreeAdapter(root)
            ca.adaptObjAndSubTree(root)
            return ca
        }
    }
}