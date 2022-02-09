package de.mf.kmf.test

import de.mf.kmf.core.debugPath
import kotlin.test.Test
import kotlin.test.assertEquals

class KmfUtilsTest {

    @Test
    fun testDebugPath() {
        val testNodeClass = TestNodeClass()
        TestRoot().apply {
            child = TestNodeClass().apply {
                id = "Foo"
                children += TestNodeClass()
                children += testNodeClass
                children += TestNodeClass()
            }
        }

        assertEquals(
            "de.mf.kmf.test.TestRoot.child/de.mf.kmf.test.TestNodeClass<Foo>.children[1]/de.mf.kmf.test.TestNodeClass",
            testNodeClass.debugPath(
                showOnlyProperties = false,
                showIds = true,
                forceFullyQualifiedClassnames = true
            )
        )

        assertEquals(
            "TestRoot.child<Foo>.children[1]",
            testNodeClass.debugPath(
                showOnlyProperties = true,
                showIds = true,
                forceFullyQualifiedClassnames = false
            )
        )

        assertEquals(
            "TestRoot.child.children[1]",
            testNodeClass.debugPath(
                showOnlyProperties = true,
                showIds = false,
                forceFullyQualifiedClassnames = false
            )
        )
    }

}