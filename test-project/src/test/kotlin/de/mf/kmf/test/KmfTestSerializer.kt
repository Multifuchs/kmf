package de.mf.kmf.test

import de.mf.kmf.core.AbstractKmfSerializer
import de.mf.kmf.core.KmfAttribute
import de.mf.kmf.core.KmfObject
import de.mf.kmf.core.debugPath
import java.time.LocalDate
import kotlin.math.min
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class KmfTestSerializer {

    @BeforeTest
    fun before() {
        SerializerMock.events.clear()
    }

    @Test
    fun testUnmodifiedObject() {
        val test = TestExtendedRoot().apply {
            id = "fooo"
        }
        SerializerMock.serialize(test, true)
        assertEventsEquals(
            listOf(
                SerializerEvent(
                    "startObject", test, null, null, emptyList()
                ),
                SerializerEvent(
                    "onSimpleProperty", test,
                    TestRoot.KmfClass.id, "fooo", emptyList()
                ),
                SerializerEvent(
                    "endObject", test, null, null, emptyList()
                ),
            ),
            SerializerMock.events
        )
    }

    @Test
    fun testPrimitiveProperties() {
        val test = TestExtendedRoot().apply {
            id = "fooo"
        }
        // Property order here is the same as in testmodel.json.
        test.simpleLocalDateProp = LocalDate.ofEpochDay(1)
        test.nullLocalDateProp = LocalDate.ofEpochDay(2)
        test.listLocalDateProp.add(LocalDate.ofEpochDay(3))

        SerializerMock.serialize(test, true)

        val expected = listOf(
            SerializerEvent(
                "onSimpleProperty", test,
                TestRoot.KmfClass.id, "fooo", emptyList()
            ),
            SerializerEvent(
                "onSimpleProperty",
                test, TestRoot.KmfClass.simpleLocalDateProp,
                LocalDate.ofEpochDay(1), emptyList()
            ),
            SerializerEvent(
                "onSimpleProperty",
                test, TestRoot.KmfClass.nullLocalDateProp,
                LocalDate.ofEpochDay(2), emptyList()
            ),
            SerializerEvent(
                "onSimpleListProperty",
                test, TestRoot.KmfClass.listLocalDateProp,
                listOf(LocalDate.ofEpochDay(3)), emptyList()
            ),
        )
        // startObject and endEvent already tested.
        val actual = SerializerMock.events.drop(1).dropLast(1)
        assertEventsEquals(expected, actual)
    }

    @Test
    fun testChildren() {
        val test = TestExtendedRoot().apply {
            id = "theChildItIs"
        }
        test.child = TestNodeClass()
        test.children += TestNodeClass().apply {
            name = "Foo123"
        }
        test.children += TestNodeClass()

        SerializerMock.serialize(test, true)

        val expected = listOf(
            SerializerEvent(
                "onSimpleProperty", test,
                TestRoot.KmfClass.id, "theChildItIs", emptyList()
            ),
            SerializerEvent(
                "startChildAttribute", test,
                TestRoot.KmfClass.child, null, emptyList()
            ),
            SerializerEvent(
                "startObject", test.child!!, null, null,
                listOf(test)
            ),
            SerializerEvent(
                "endObject", test.child!!, null, null,
                listOf(test)
            ),
            SerializerEvent(
                "endChildAttribute", test, TestRoot.KmfClass.child,
                null, emptyList()
            ),
            SerializerEvent(
                "startChildAttribute", test, TestRoot.KmfClass.children,
                null, emptyList()
            ),
            SerializerEvent(
                "startObject", test.children[0], null, null,
                listOf(test)
            ),
            SerializerEvent(
                "onSimpleProperty", test.children[0],
                TestNodeClass.KmfClass.name, "Foo123", listOf(test)
            ),
            SerializerEvent(
                "endObject", test.children[0], null, null,
                listOf(test)
            ),
            SerializerEvent(
                "startObject", test.children[1], null, null,
                listOf(test)
            ),
            SerializerEvent(
                "endObject", test.children[1], null, null,
                listOf(test)
            ),
            SerializerEvent(
                "endChildAttribute", test, TestRoot.KmfClass.children,
                null, emptyList()
            ),
        )
        // startObject and endEvent already tested.
        val actual = SerializerMock.events.drop(1).dropLast(1)

        assertEventsEquals(expected, actual)
    }

    private fun assertEventsEquals(
        expected: List<SerializerEvent>,
        actual: List<SerializerEvent>
    ) {
        for (i in 0..(min(expected.lastIndex, actual.lastIndex))) {
            assertEquals(expected[i], actual[i], buildString {
                append("Index $i").appendLine()
                append("Expected: ${expected[i].func} obj: ${expected[i].obj.debugPath()}.${expected[i].prop} value: ${expected[i].value}").appendLine()
                append("Actual  : ${actual[i].func} obj: ${actual[i].obj.debugPath()}.${actual[i].prop} value: ${actual[i].value}").appendLine()
            })
        }

        assertEquals(expected.size, actual.size)
    }

    data class SerializerEvent(
        val func: String,
        val obj: KmfObject,
        val prop: KmfAttribute?,
        val value: Any?,
        val parents: List<KmfObject>
    )

    object SerializerMock : AbstractKmfSerializer() {

        val events = mutableListOf<SerializerEvent>()

        fun serialize(obj: KmfObject, ignoreDefaultValues: Boolean) {
            this.ignoreDefaultValues = ignoreDefaultValues
            execSerialize(obj)
        }

        override fun startObject(
            obj: KmfObject,
            serializedParents: List<KmfObject>
        ) {
            events += SerializerEvent(
                "startObject", obj, null, null, serializedParents.toList()
            )
        }

        override fun endObject(
            obj: KmfObject,
            serializedParents: List<KmfObject>
        ) {
            events += SerializerEvent(
                "endObject", obj, null, null,
                serializedParents.toList()
            )
        }

        override fun onSimpleProperty(
            obj: KmfObject,
            prop: KmfAttribute,
            value: Any?,
            serializedParents: List<KmfObject>
        ) {
            events += SerializerEvent(
                "onSimpleProperty", obj, prop, value,
                serializedParents.toList()
            )
        }

        override fun onSimpleListProperty(
            obj: KmfObject,
            prop: KmfAttribute,
            values: List<Any>,
            serializedParents: List<KmfObject>
        ) {
            events += SerializerEvent(
                "onSimpleListProperty", obj, prop, values,
                serializedParents.toList()
            )
        }

        override fun onReferenceProperty(
            obj: KmfObject,
            prop: KmfAttribute,
            refObj: KmfObject?,
            serializedParents: List<KmfObject>
        ) {
            events += SerializerEvent(
                "onReferenceProperty", obj, prop, refObj,
                serializedParents.toList()
            )
        }

        override fun onReferenceListProperty(
            obj: KmfObject,
            prop: KmfAttribute,
            refObjList: List<KmfObject>,
            serializedParents: List<KmfObject>
        ) {
            events += SerializerEvent(
                "onReferenceListProperty", obj, prop, refObjList,
                serializedParents.toList()
            )
        }

        override fun startChildAttribute(
            obj: KmfObject,
            prop: KmfAttribute,
            serializedParents: List<KmfObject>
        ) {
            events += SerializerEvent(
                "startChildAttribute",
                obj,
                prop,
                null,
                serializedParents.toList()
            )
        }

        override fun endChildAttribute(
            obj: KmfObject,
            prop: KmfAttribute,
            serializedParents: List<KmfObject>
        ) {
            events += SerializerEvent(
                "endChildAttribute", obj, prop, null, serializedParents.toList()
            )
        }
    }


}