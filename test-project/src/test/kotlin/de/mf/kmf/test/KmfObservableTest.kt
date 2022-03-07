package de.mf.kmf.test

import de.mf.kmf.core.KmfNotification
import de.mf.kmf.observer.*
import kotlin.test.Test
import kotlin.test.assertEquals

class KmfObservableTest {

    @Test
    fun testSimple() {
        val root = TestRoot().apply {
            friend = TestRoot().apply {
                child = TestNodeClass().apply {
                    name = "Foo"
                }
            }
        }
        val valPath = KmfObjPath.startAt(TestRoot::class) /
            TestRoot::friend / TestRoot::child value TestNodeClass::name

        val prop = UnaryObservedKMFObj(root, valPath)

        assertEquals("Foo", prop.getValue())
        assertEquals(1, root.friend!!.child!!.adapters.size)

        val events = mutableListOf<KMFValueEvent<String>>()
        prop.addListener {
            events += it
        }

        assertEquals(1, events.size)
        assert(events[0] is KMFValueEvent.Resolved)
        val e1 = events[0] as KMFValueEvent.Resolved
        assertEquals("Foo", e1.value)

        events.clear()
        root.friend!!.child!!.name = "Bar"

        assertEquals(
            KMFValueEvent.Resolved<String>(
                prop,
                "Bar",
                KmfNotification.Set(
                    root.friend!!.child!!,
                    TestNodeClass.name,
                    "Foo",
                    "Bar"
                )
            ),
            events[0]
        )

        events.clear()
        val oldFriend = root.friend!!
        root.friend = null
        assertEquals(1, events.size)
        assertEquals(KMFValueEvent.Unresolved(prop), events[0])
        assert(oldFriend.adapters.isEmpty())
        assert(oldFriend.child!!.adapters.isEmpty())

        events.clear()
        root.friend = TestRoot()
        assert(events.isEmpty())

        events.clear()
        root.friend!!.child = TestNodeClass()
        assertEquals(1, events.size)
        assertEquals(
            KMFValueEvent.Resolved(
                prop,
                TestNodeClass.name.defaultValue as String,
                null
            ),
            events[0]
        )
    }

}