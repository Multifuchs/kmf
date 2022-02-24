package de.mf.kmf.test

import de.mf.kmf.observer.KmfObjPath
import de.mf.kmf.observer.div
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class KmfObjectPathTests {

    @Test
    fun resolveTest() {
        val path = KmfObjPath.startAt(TestRoot::class) / TestRoot::child

        val root = TestRoot()

        assertNull(path.resolve(root))

        root.child = TestNodeClass()
        assertSame(root.child, path.resolve(root))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testBrokenManualPath() {
        KmfObjPath.build(TestNodeClass::class, TestExtendedRoot::class, listOf(TestRoot.child))
    }
}