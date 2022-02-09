package de.mf.kmf.test

import de.mf.kmf.core.KmfException
import kotlin.test.*

class KmfObjectChildrenTests {

    var root = TestRoot()
    var extendedRoot = TestExtendedRoot()
    var node = TestNodeClass()

    @BeforeTest
    fun prepareModel() {
        root = TestRoot()
        extendedRoot = TestExtendedRoot()
        node = TestNodeClass()
    }

    @Test
    fun testSingleParent() {
        val rootA = root
        val rootB = TestRoot()
        val child = node

        // Test that a KmfObject can exist only once in a children tree.

        rootA.child = child
        assertSame(child, rootA.child)
        assertSame(rootA, child.parent)

        // child is moved to another property of the same parent.
        rootA.children += child
        assertSame(null, rootA.child)
        assertContains(rootA.children, child)
        assertSame(rootA, child.parent)

        // child is moved to a list, where it exists, already.
        rootA.children += child
        assertEquals(1, rootA.children.size)
        assertContains(rootA.children, child)
        assertSame(rootA, child.parent)

        // child is moved to another parent.
        rootB.child = child
        assertSame(null, rootA.child)
        assertFalse(rootA.children.contains(child))
        assertSame(child, rootB.child)
        assertSame(rootB, child.parent)

        // child is removed from parent.
        rootB.child = null
        assertSame(null, rootB.child)
        assertSame(null, child.parent)
    }

    @Test(expected = KmfException::class)
    fun testRecursiveChildrenFail() {
        val nodes = listOf(
            TestNodeClass(),
            TestNodeClass(),
            TestNodeClass()
        )

        nodes.forEachIndexed { index, node ->
            val nextNode = nodes[(index + 1) % nodes.size]
            node.children += nextNode
        }
    }

}