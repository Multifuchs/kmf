package de.mf.kmf.test

import de.mf.kmf.core.*
import de.mf.kmf.json.deserializeKmfJson
import de.mf.kmf.json.writeJson
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.reflect.full.isSubclassOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class KmfSerializerTest {

    @Test
    fun deserializerTest() {
        val outsideObject = TestNodeClass().apply {
            id = "outsider"
            children.add(TestNodeClass())
            children.add(TestNodeClass())
        }
        assertEquals(2, outsideObject.children.size)
        val resolver = KmfResolver(listOf(outsideObject))

        var actualResult: KmfObject? = null
        val deserializer = KmfDeserializerTool().apply {
            this.resolver = resolver
            startObject(TestRoot.KmfClass)
            run {
                startAttribute("simpleStringProp")
                addSimpleValue("simpleFoo")
                startAttribute("nullLongProp")
                addSimpleValue(42L)
                startAttribute("listLocalDateProp")
                addSimpleValue(LocalDate.ofEpochDay(1))
                addSimpleValue(LocalDate.ofEpochDay(2))

                startAttribute("singleRef")
                addReferenceValue("<id:innerObj>.children[0]")
                startAttribute("multiRef")
                addReferenceValue("<id:outsider>")
                addReferenceValue("<id:innerObj>.children[0]")
                addReferenceValue("<id:outsider>.children[1]")

                startAttribute("children")
                startObject(TestNodeClass.KmfClass)
                run {
                    startAttribute("id")
                    addSimpleValue("innerObj")
                    startAttribute("children")
                    startObject(TestNodeClass.KmfClass)
                    run {
                        startAttribute("name")
                        addSimpleValue("innerFoo")
                    }
                    endObject()
                }
                endObject()
            }
            actualResult = endObject()
        }

        val expected = TestRoot().apply {
            simpleStringProp = "simpleFoo"
            nullLongProp = 42L
            listLocalDateProp += LocalDate.ofEpochDay(1)
            listLocalDateProp += LocalDate.ofEpochDay(2)

            children += TestNodeClass().apply {
                id = "innerObj"
                children += TestNodeClass().apply {
                    name = "innerFoo"
                }
            }

            singleRef = children[0].children[0]
            multiRef += outsideObject
            multiRef += children[0].children[0]
            multiRef += outsideObject.children[1]
        }

        assert(expected deepEquals actualResult) {
            buildString {
                appendLine("Expected:")
                appendLine(expected)
                appendLine("Actual:")
                appendLine(actualResult)
            }
        }
    }

    @Test
    fun serializerTest() {
        val other = TestRoot().apply {
            id = "other"
            children += TestNodeClass()
            children += TestNodeClass().apply {
                id = "nodeWId"
            }
        }
        val root = TestRoot().apply {
            id = "test"
            simpleStringProp = "Foo"
            listStringProp += "Bar42"
            listStringProp += "Bar1337"
            simpleBooleanProp = true
            listBooleanProp += true
            listBooleanProp += false
            simpleIntProp = 42
            listIntProp += 4242
            listIntProp += 1337
            simpleLongProp = 4200L
            listLongProp += 42004200L
            listLongProp += 13371337L
            simpleDoubleProp += 42.42
            listDoubleProp += 4242.4242
            listDoubleProp += 1337.1337
            simpleLocalDateProp = LocalDate.ofEpochDay(1)
            listLocalDateProp += LocalDate.ofEpochDay(2)
            listLocalDateProp += LocalDate.ofEpochDay(3)
            simpleOffsetDateTimeProp = OffsetDateTime.of(
                LocalDate.ofEpochDay(3), LocalTime.NOON, ZoneOffset.UTC
            )
            child = TestNodeClass()
            children += TestNodeClass().apply {
                name = "Child One of TestRoot"
                children += TestNodeClass().apply {
                    name ="Inner child of TestRoot"
                }
            }
            singleRef = child
            multiRef += other.children[0]
            multiRef += other.children[1]
        }

        val sb = StringWriter()
        sb.use {
            root.writeJson(it)
        }
//        sb.toString().lineSequence()
//            .mapIndexed { index, s -> "$index:  $s" }
//            .forEach { println(it) }

        val deserialized =
            deserializeKmfJson(
                TestRoot::class,
                KmfResolver(listOf(other)),
                StringReader(sb.toString())
            )

        assertTrue(root deepEquals deserialized)
    }
}