package de.mf.kmf.test

import de.mf.kmf.observer.*
import kotlin.test.Test

class KmfAttrPathTest {

    @Test
    fun testBuilder() {
        val root = TestRoot()

        val path1 = root pathToKmfObj TestRoot::child
        val path2 = root pathToValue TestRoot::nullStringProp
        val path3 = root pathToList TestRoot::listStringProp
        val extendedPath1 =
            root pathToKmfObj TestRoot::child toValue TestNodeClass::name
        val extendedPath2 =
            root pathToKmfObj TestRoot::child toList TestNodeClass::children
    }

}