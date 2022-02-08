package de.mf.kmf.test

import de.mf.kmf.core.*
import java.time.LocalDate
import kotlin.math.exp
import kotlin.reflect.full.companionObjectInstance
import kotlin.reflect.full.isSubclassOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class KmfDeserializerTest {

    @Test
    fun fullDeserializerTest() {
        val outsideObject = TestNodeClass().apply {
            id = "outsider"
            children.add(TestNodeClass())
            children.add(TestNodeClass())
        }
        assertEquals(2, outsideObject.children.size)
        val resolver = KmfResolver(listOf(outsideObject))

        var actualResult: KmfObject? = null
        val deserializer = object : AbstractKotlinDeserializer() {
            init {
                reset()
                this.resolver = resolver
                startObject(TestRoot.KmfClass)
                run {
                    startAttribute("simpleStringProp")
                    addSimpleValue("simpleFoo")
                    endAttribute()
                    startAttribute("nullLongProp")
                    addSimpleValue(42L)
                    endAttribute()
                    startAttribute("listLocalDateProp")
                    addSimpleValue(LocalDate.ofEpochDay(1))
                    addSimpleValue(LocalDate.ofEpochDay(2))
                    endAttribute()

                    startAttribute("singleRef")
                    addReferenceValue("<id:innerObj>.children[0]")
                    endAttribute()
                    startAttribute("multiRef")
                    addReferenceValue("<id:outsider>")
                    addReferenceValue("<id:innerObj>.children[0]")
                    addReferenceValue("<id:outsider>.children[1]")
                    endAttribute()

                    startAttribute("children")
                    startObject(TestNodeClass.KmfClass)
                    run {
                        startAttribute("id")
                        addSimpleValue("innerObj")
                        endAttribute()
                        startAttribute("children")
                        startObject(TestNodeClass.KmfClass)
                        run {
                            startAttribute("name")
                            addSimpleValue("innerFoo")
                            endAttribute()
                        }
                        endObject()
                        endAttribute()
                    }
                    endObject()
                    endAttribute()
                }
                actualResult = endObject()
            }
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
}