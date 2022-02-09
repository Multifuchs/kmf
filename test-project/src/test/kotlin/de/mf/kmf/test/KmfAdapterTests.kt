package de.mf.kmf.test

import de.mf.kmf.core.KmfAdapter
import de.mf.kmf.core.KmfNotification
import de.mf.kmf.core.KmfObject
import org.junit.Test
import kotlin.test.BeforeTest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class KmfAdapterTests {

    var rootA = TestRoot()
    var rootB = TestRoot()
    var child = TestNodeClass()

    val onAdapt = mutableListOf<KmfObject>()
    val onNotify = mutableListOf<KmfNotification>()

    @BeforeTest
    fun prepare() {
        rootA = TestRoot()
        rootB = TestRoot()
        child = TestNodeClass()

        onAdapt.clear()
        onNotify.clear()

        val adapter = object : KmfAdapter {
            override fun onAdapt(obj: KmfObject) {
                onAdapt += obj
            }

            override fun notify(notification: KmfNotification) {
                onNotify += notification
            }
        }

        rootA.adapt(adapter.javaClass) { adapter }
        rootB.adapt(adapter.javaClass) { adapter }
        child.adapt(adapter.javaClass) { adapter }
    }

    @Test
    fun testOnAdapt() {
        // Adapter must be added to these kmfObjects in this order.
        assertEquals(rootA, onAdapt[0])
        assertEquals(rootB, onAdapt[1])
        assertEquals(child, onAdapt[2])
    }

    @Test
    fun testSimpleProperties() {
        rootA.nullStringProp = "Foo"
        rootA.nullStringProp = null
        assertEquals(
            KmfNotification.Set(
                rootA, TestRoot.KmfClass.nullStringProp, null, "Foo"
            ), onNotify[0]
        )
        assertEquals(
            KmfNotification.Set(
                rootA, TestRoot.KmfClass.nullStringProp, "Foo", null
            ), onNotify[1]
        )
    }

    @Test
    fun testListProperties() {
        rootA.listStringProp += "Foo"
        rootA.listStringProp[0] = "Bar"
        rootA.listStringProp.clear()
        assertEquals(
            KmfNotification.ListInsert(
                rootA, TestRoot.KmfClass.listStringProp, 0, "Foo"
            ), onNotify[0]
        )
        assertEquals(
            KmfNotification.ListSet(
                rootA, TestRoot.KmfClass.listStringProp, 0, "Foo", "Bar"
            ), onNotify[1]
        )
        assertEquals(
            KmfNotification.ListRemove(
                rootA, TestRoot.KmfClass.listStringProp, 0, "Bar"
            ),
            onNotify[2]
        )
    }

    @Test
    fun testChildReferences() {
        rootA.child = child
        rootA.child = null
        rootA.children += child
        rootA.children += child
        rootB.child = child
        rootB.child = null

        assertContentEquals(
            listOf(
                KmfNotification.Set(
                    rootA, TestRoot.KmfClass.child, null, child
                ),
                KmfNotification.Parent(
                    child, null, rootA
                ),

                KmfNotification.Set(
                    rootA, TestRoot.KmfClass.child, child, null
                ),
                KmfNotification.Parent(
                    child, rootA, null
                ),

                KmfNotification.ListInsert(
                    rootA, TestRoot.KmfClass.children, 0, child
                ),
                KmfNotification.Parent(
                    child, null, rootA
                ),
                // 2nd rootA.children += child is expected to be ignored,
                // because child is a child of rootA, already.

                KmfNotification.ListRemove(
                    rootA, TestRoot.KmfClass.children, 0, child
                ),
                KmfNotification.Set(
                    rootB, TestRoot.KmfClass.child, null, child
                ),
                KmfNotification.Parent(
                    child, rootA, rootB
                ),

                KmfNotification.Set(
                    rootB, TestRoot.KmfClass.child, child, null
                ),
                KmfNotification.Parent(
                    child, rootB, null
                )
            ),
            onNotify
        )
    }

}