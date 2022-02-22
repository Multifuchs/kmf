package de.mf.kmf.test

import de.mf.kmf.observer.*
import kotlin.test.*

class KmfAttrPathTest {

    @Test
    fun testBuilder() {

        val path1 = TestRoot::class pathToKmfObj TestRoot::child
        val path2 = TestRoot::class pathToValue TestRoot::nullStringProp
        val path3 = TestRoot::class pathToList TestRoot::listStringProp
        val extendedPath1 =
            TestRoot::class pathToKmfObj TestRoot::child toValue TestNodeClass::name
        val extendedPath2 =
            TestRoot::class pathToKmfObj TestRoot::child toList TestNodeClass::children
    }

    @Test(IllegalArgumentException::class)
    fun testIllegalKind() {
        TestRoot.KmfClass.attrPathToValue(
            listOf(TestRoot.KmfClass.simpleStringProp),
            TestRoot.KmfClass.id
        )
    }

    @Test(IllegalArgumentException::class)
    fun testPropertyNotAMemberOfParent() {
        TestRoot.KmfClass.attrPathToValue(listOf(), TestNodeClass.KmfClass.name)
    }

    @Test
    fun testGetWithPrimitiveUnary() {
        val path =
            TestRoot::class pathToKmfObj TestRoot::child toValue TestNodeClass::name
        val root = TestRoot()
        assertEquals(null, path.getValue(root))
        root.child = TestNodeClass()
        assertEquals("", path.getValue(root))
        root.child!!.name = "Foo"
        assertEquals("Foo", path.getValue(root))
    }

    @Test
    fun testGetWithList() {
        val path =
            TestRoot::class pathToKmfObj TestRoot::child toList TestNodeClass::children
        val root = TestRoot()
        assertEquals(null, path.getValue(root))
        root.child = TestNodeClass()
        assertNotNull(path.getValue(root))
        assertSame(root.child!!.children, path.getValue(root))
        root.child!!.children += TestNodeClass()
        assertSame(root.child!!.children, path.getValue(root))
    }

    @Test
    fun testSet() {
        val path =
            TestRoot::class pathToKmfObj TestRoot::child toValue TestNodeClass::name
        val root = TestRoot()
        val node = TestNodeClass()
        root.child = node
        path.setValue(root, "Foo Bar")
        assertEquals("Foo Bar", root.child!!.name)
    }

}