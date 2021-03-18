package de.mf.kmf.codegen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.slf4j.Logger
import java.io.Reader
import java.nio.file.Path
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.ExperimentalPathApi

object KmfTypes {
    val KMF_CORE_PACKAGE = "de.mf.kmf.core"
    val KMF_OBJECT = ClassName(KMF_CORE_PACKAGE, "KmfObject")
    val KMF_CLASS = ClassName(KMF_CORE_PACKAGE, "KmfClass")
    val KMF_ATTRIBUTE = ClassName(KMF_CORE_PACKAGE, "KmfAttribute")
    val KMF_ATTRIBUTE_UNARY = KMF_ATTRIBUTE.nestedClass("Unary")
    val KMF_ATTRIBUTE_LIST = KMF_ATTRIBUTE.nestedClass("List")
    val KMF_ATTRIBUTE_KIND = ClassName(KMF_CORE_PACKAGE, "KmfAttrKind")
    val KMF_NOTIFICATION = ClassName(KMF_CORE_PACKAGE, "KmfNotification")
    fun kmfList(valueType: TypeName) = ClassName(KMF_CORE_PACKAGE, "KmfList")
        .parameterizedBy(valueType)
}

object PrimitiveTypes {

    private val DATE_TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val DATE_FORMAT = DateTimeFormatter.ISO_DATE

    val STRING = String::class.asClassName()
    val INT = Int::class.asClassName()
    val LONG = Long::class.asClassName()
    val DOUBLE = Double::class.asClassName()
    val BOOLEAN = Int::class.asClassName()
    val DATE_TIME = OffsetDateTime::class.asClassName()
    val DATE = LocalDate::class.asClassName()

    fun getByName(name: String) = when (name) {
        "String" -> STRING
        "Int" -> INT
        "Long" -> LONG
        "Double" -> DOUBLE
        "Boolean" -> BOOLEAN
        "DateTime" -> DATE_TIME
        "Date" -> DATE
        else -> null
    }

    fun parseDefaultValue(type: ClassName, value: Any?): CodeBlock = try {
        val converted: Any = when (type) {
            STRING -> value?.toString() ?: "\"\""
            INT -> (value as? Number)?.toInt()
                ?: value?.toString()?.toInt() ?: "0"
            LONG -> (value as? Number)?.toLong()
                ?: value?.toString()?.toLong() ?: "0L"
            DOUBLE -> (value as? Number)?.toDouble()
                ?: value?.toString()?.toDouble() ?: "0.0"
            BOOLEAN -> (value as? Boolean)
                ?: value?.toString()?.toBoolean()
                ?: "false"
            DATE_TIME -> CodeBlock.of(
                "%T.parse(%S, %T.ISO_OFFSET_DATE_TIME)",
                OffsetDateTime::class.java,
                value.toString(),
                DateTimeFormatter::class.java
            )
            DATE -> CodeBlock.of(
                "%T.parse(%S, %T.ISO_DATE)",
                LocalDate::class.java,
                value.toString(),
                DateTimeFormatter::class.java
            )
            // Assume it is an Enum
            else -> {
                if (value == null) CodeBlock.of("%T.values().first()", type)
                else CodeBlock.of("%T.${value.toString()}", type)
            }
        }
        (converted as? CodeBlock) ?: CodeBlock.of(converted.toString())
    } catch (e: Exception) {
        throw Exception(
            "Can't parse default value of type $type: '$value'\n${e.message}",
            e
        )
    }
}

@ExperimentalPathApi
fun generateKmfCode(
    jsonInput: Reader,
    generatedSourceDir: Path,
    logger: Logger? = null
) {
    val parsed = jsonInput.use { parseJson(it) }

    val fileSpec = FileSpec.builder(parsed.packageName, "Model").apply {
        parsed.classes.forEach { parsedClass ->
            addType(buildObjectType(parsed, parsedClass))
        }
    }.build()

    fileSpec.writeTo(generatedSourceDir)
}

private fun buildObjectType(parsedRoot: RootDesc, parsedClass: ClassDesc) =
    TypeSpec.classBuilder(parsedClass.name).apply {
        modifiers += KModifier.OPEN
        superclass(
            parsedClass.superClass
                ?.let { buildClassName(parsedRoot, parsedClass.superClass) }
                ?: KmfTypes.KMF_OBJECT
        )
        parsedClass.attributes.forEach { attrDesc ->
            buildPropertySpecs(
                parsedRoot,
                parsedClass,
                attrDesc
            ).forEach { ps -> addProperty(ps) }
        }

        addProperty(
            PropertySpec.builder(
                "kmfClass",
                KmfTypes.KMF_CLASS,
                KModifier.OVERRIDE
            ).getter(
                FunSpec.getterBuilder()
                    .addStatement("return KmfClass")
                    .build()
            ).build()
        )

        addFunction(FunSpec.builder("internalSetValue").apply {
            modifiers += KModifier.PROTECTED
            modifiers += KModifier.OVERRIDE
            /*
            attribute: KmfAttribute,
        value: Any?,
        notifySet: Boolean,
        /** For [KmfAttrKind.CHILD] only*/
        notifyParent: Boolean
             */
            addParameter("attribute", KmfTypes.KMF_ATTRIBUTE)
            addParameter(
                "value",
                Any::class.asClassName().copy(nullable = true)
            )
            val oneAttributes = parsedClass.attributes.filter {
                it.multiplicity == AttrDescMultiplicity.ONE
            }
            if (oneAttributes.isNotEmpty()) {
                beginControlFlow("when")
                oneAttributes.forEach { ad ->
                    beginControlFlow("attribute === KmfClass.${ad.name} ->")
                    if (ad.nullable) {
                        addStatement("${ad.internalPropName} = if (value == null) null")
                        addStatement(
                            "else %T::class.java.cast(value)",
                            buildClassName(parsedRoot, ad.valueType)
                        )
                    } else {
                        addStatement(
                            "${ad.internalPropName} = %T::class.java.cast(value)",
                            buildClassName(parsedRoot, ad.valueType)
                        )
                    }
                    addStatement("true")
                    endControlFlow()
                }

                beginControlFlow("else ->")
                addStatement("super.internalSetValue(attribute, value)")
                addStatement("false")
                endControlFlow() // else ->
                endControlFlow() // when {
            }
        }.build())

        addType(buildKmfClassObject(parsedRoot, parsedClass))
    }.build()

private fun buildPropertySpecs(
    parsedRoot: RootDesc,
    parsedClass: ClassDesc,
    attrDesc: AttrDesc
): List<PropertySpec> {
    val propValueType = when (attrDesc.type) {
        AttrDescType.PROPERTY ->
            PrimitiveTypes.getByName(attrDesc.valueType)
                ?: buildClassName(parsedRoot, attrDesc.valueType)

        AttrDescType.REFERENCE,
        AttrDescType.CHILD -> buildClassName(parsedRoot, attrDesc.valueType)
    }
    val propType = propValueType.let {
        if (attrDesc.multiplicity == AttrDescMultiplicity.MANY)
            KmfTypes.kmfList(it)
        else
            it.copy(nullable = attrDesc.nullable)
    }

    val internalPropName = attrDesc.internalPropName

    val internalProp = PropertySpec.builder(
        internalPropName,
        propType
    ).apply {
        // Setup initializer
        modifiers += KModifier.PRIVATE
        mutable()
        if (attrDesc.type == AttrDescType.PROPERTY) {
            when {
                attrDesc.multiplicity == AttrDescMultiplicity.MANY ->
                    initializer(
                        "createSimpleList(%T::class.java)",
                        propValueType
                    )
                attrDesc.nullable && attrDesc.defaultValue == null ->
                    initializer("null")
                else -> initializer(
                    PrimitiveTypes.parseDefaultValue(
                        propValueType,
                        attrDesc.defaultValue
                    )
                )
            }
        } else {
            initializer("null")
        }
    }.build()
        .takeIf { attrDesc.multiplicity == AttrDescMultiplicity.ONE }

    val mainProp = PropertySpec.builder(
        attrDesc.name,
        propType
    ).apply {
        // Setup val/var and getter/setter
        when (attrDesc.multiplicity) {
            AttrDescMultiplicity.ONE -> {
                mutable()
                getter(
                    FunSpec.getterBuilder()
                        .addStatement("return $internalPropName")
                        .build()
                )
                if (attrDesc.type == AttrDescType.CHILD) {
                    setter(
                        FunSpec.setterBuilder().addParameter("value", propType)
                            .addStatement("if ($internalPropName == value) return")
                            .beginControlFlow("if (value === null)")
                            .addStatement("$internalPropName!!.internalSetParent(null, null)")
                            .nextControlFlow("else")
                            .addStatement("value.internalSetParent(this, KmfClass.${attrDesc.name})")
                            .endControlFlow()
                            .build()
                    )
                } else {
                    setter(
                        FunSpec.setterBuilder()
                            .addParameter("value", propType)
                            .addStatement("if ($internalPropName == value) return")
                            .addStatement("val old = $internalPropName")
                            .addStatement("$internalPropName = value")
                            .addStatement(
                                "if(canNotify()) notify(%T(this, KmfClass.${attrDesc.name}, old, value))",
                                KmfTypes.KMF_NOTIFICATION.nestedClass("Set")
                            )
                            .build()
                    )
                }
            }
            AttrDescMultiplicity.MANY -> {
                if (attrDesc.type == AttrDescType.CHILD)
                    initializer("createChildrenList(KmfClass.${attrDesc.name})")
                else
                    initializer("createSimpleList(KmfClass.${attrDesc.name})")
            }
        }

    }.build()

    return listOfNotNull(internalProp, mainProp)
}

private fun buildKmfClassObject(parsedRoot: RootDesc, parsedClass: ClassDesc) =
    TypeSpec.objectBuilder("KmfClass").apply {
        superclass(KmfTypes.KMF_CLASS)
        superclassConstructorParameters += CodeBlock.of("${parsedClass.name}::class")
        superclassConstructorParameters += if (parsedClass.superClass == null)
            CodeBlock.of("null")
        else CodeBlock.of(
            "%T.KmfClass",
            buildClassName(parsedRoot, parsedClass.superClass)
        )

        parsedClass.attributes.forEach { ad ->
            val kind = ad.type.name
            val attrType = when (ad.multiplicity) {
                AttrDescMultiplicity.ONE -> KmfTypes.KMF_ATTRIBUTE_UNARY
                AttrDescMultiplicity.MANY -> KmfTypes.KMF_ATTRIBUTE_LIST
            }
            val initializer = when (ad.multiplicity) {
                AttrDescMultiplicity.ONE ->
                    CodeBlock.of(
                        "%T(this, %T::class, %T.$kind, ${ad.nullable}, %T::${ad.name})",
                        attrType,
                        buildClassName(parsedRoot, ad.valueType),
                        KmfTypes.KMF_ATTRIBUTE_KIND,
                        buildClassName(
                            parsedRoot,
                            parsedClass.name
                        )
                    )
                AttrDescMultiplicity.MANY ->
                    CodeBlock.of(
                        "%T(this, %T::class, %T.$kind, %T::${ad.name})",
                        attrType,
                        buildClassName(parsedRoot, ad.valueType),
                        KmfTypes.KMF_ATTRIBUTE_KIND,
                        buildClassName(parsedRoot, parsedClass.name)
                    )
            }
            addProperty(
                PropertySpec.builder(ad.name, attrType)
                    .initializer(initializer)
                    .build()
            )
        }

        addInitializerBlock(
            CodeBlock.builder()
                .apply {
                    parsedClass.attributes.forEach { ad ->
                        addStatement("addAttribute(${ad.name})")
                    }
                }
                .build()
        )
    }.build()

private fun buildClassName(parsedRoot: RootDesc, name: String): ClassName {
    val lastDot = name.lastIndexOf('.')
    return if (lastDot != -1)
    // Fully qualified classname
        ClassName(name.substring(0, lastDot), name.substring(lastDot + 1))
    else {
        // Use user-defined import, if available.
        // Otherwise take package name from rootDesc
        val importedPackage = parsedRoot.imports[name]
        ClassName(importedPackage ?: parsedRoot.packageName, name)
    }
}

private val AttrDesc.internalPropName get() = "${name}__value"
