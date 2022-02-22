package de.mf.kmf.test

import de.mf.kmf.observer.*
import kotlin.test.Test

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
        TestRoot.KmfClass.attrPathToValue(listOf(TestRoot.KmfClass.simpleStringProp), TestRoot.KmfClass.id)
    }

    @Test(IllegalArgumentException::class)
    fun testPropertyNotAMemberOfParent() {
        TestRoot.KmfClass.attrPathToValue(listOf(), TestNodeClass.KmfClass.name)
    }

}