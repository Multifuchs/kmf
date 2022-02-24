package de.mf.kmf.test

import de.mf.kmf.observer.*
import kotlin.test.*

class KmfValuePathTest {

    @Test
    fun testResolveSingleValue() {
        val vPath =
            KmfObjPath.startAt(TestRoot::class) / TestRoot::child value TestNodeClass::name
        val root = TestRoot()
        assertNull(vPath.get(root))
        assert(vPath.getKmfValue(root) is KmfUnresolvedValue<*>)
        assertFalse(vPath.set(root, "Anything"))

        root.child = TestNodeClass()
        assertEquals(vPath.get(root), "")
        assertEquals(vPath.getKmfValue(root), KmfResolvedValue(""))

        root.child!!.name = "Foo"
        assertEquals(vPath.get(root), "Foo")

        assert(vPath.set(root, "Bar"))
        assertEquals(vPath.get(root), "Bar")
    }

    @Test
    fun testResolveListValue() {
        val vPath =
            KmfObjPath.startAt(TestRoot::class) / TestRoot::friend list TestRoot::listStringProp

        val root = TestRoot()
        assertNull(vPath.get(root))

        root.friend = TestRoot()
        assertEquals(
            KmfResolvedValue(root.friend!!.listStringProp),
            vPath.getKmfValue(root)
        )

        vPath.get(root)!!.add("Foo")
        assertContentEquals(listOf("Foo"), vPath.get(root))
    }

}