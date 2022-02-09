package de.mf.kmf.test

import de.mf.kmf.core.KmfResolver
import de.mf.kmf.core.path
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class KmfResolveTest {

    @Test(expected = IllegalStateException::class)
    fun testPathWOIdFails() {
        val root = TestNodeClass().apply {
            children += TestNodeClass()
        }
        root.children[0].path()
    }

    @Test
    fun testSingleObjectPath() {
        val root = TestNodeClass().apply { id = "foo" }
        assertEquals("<id:foo>", root.path())
    }

    @Test
    fun testPathWithUnaryChild() {
        val root = TestRoot().apply {
            id = "foo"
            child = TestNodeClass()
        }
        assertEquals("<id:foo>.child", root.child!!.path())
    }

    @Test
    fun testPathWithChildrenList() {
        val root = TestRoot().apply {
            id = "foo"
            children += TestNodeClass()
            children += TestNodeClass()
            children += TestNodeClass()
        }
        assertEquals("<id:foo>.children[1]", root.children[1].path())
    }

    @Test
    fun testPathWithMultipleIds() {
        val root = TestNodeClass().apply {
            id = "foo1"
            children += TestNodeClass().apply {
                id = "foo2"
                children += TestNodeClass()
            }
        }
        assertEquals("<id:foo2>", root.children[0].path())
        assertEquals(
            "<id:foo2>.children[0]",
            root.children[0].children[0].path()
        )
    }

    @Test
    fun testResolve() {
        val rootA = TestNodeClass().apply {
            id = "rootA"
            children += TestNodeClass().apply {
                id = "rootAC1"
                children += TestNodeClass().apply {
                    children += TestNodeClass()
                    children += TestNodeClass()
                }
            }
        }

        val rootB = TestRoot().apply {
            id = "rootB"
            child = TestNodeClass()
        }

        val r = KmfResolver(listOf(rootA, rootB))
        assertSame(rootA, r.resolve("<id:rootA>"))
        assertSame(rootA.children[0], r.resolve("<id:rootAC1>"))
        assertSame(rootA.children[0].children[0], r.resolve("<id:rootAC1>.children[0]"))
        assertSame(rootA.children[0].children[0].children[0], r.resolve("<id:rootAC1>.children[0].children[0]"))
        assertSame(rootA.children[0].children[0].children[1], r.resolve("<id:rootAC1>.children[0].children[1]"))

        assertSame(rootB, r.resolve("<id:rootB>"))
        assertSame(rootB.child, r.resolve("<id:rootB>.child"))
    }

}