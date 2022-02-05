package de.mf.kmf.codegen

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.Reader
import java.nio.file.Path
import java.time.*
import java.time.format.DateTimeFormatter

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

    val STRING = String::class.asClassName()
    val INT = Int::class.asClassName()
    val LONG = Long::class.asClassName()
    val DOUBLE = Double::class.asClassName()
    val BOOLEAN = Boolean::class.asClassName()
    val LOCAL_DATE_TIME = LocalDateTime::class.asClassName()
    val LOCAL_DATE = LocalDate::class.asClassName()
    val OFFSET_DATE_TIME = OffsetDateTime::class.asClassName()
    val ZONED_DATE_TIME = ZonedDateTime::class.asClassName()

    fun getByName(name: String) = when (name) {
        "String" -> STRING
        "Int" -> INT
        "Long" -> LONG
        "Double" -> DOUBLE
        "Boolean" -> BOOLEAN
        "LocalDateTime" -> LOCAL_DATE_TIME
        "LocalDate" -> LOCAL_DATE
        "OffsetDateTime" -> OFFSET_DATE_TIME
        "ZonedDateTime" -> ZONED_DATE_TIME
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
            LOCAL_DATE -> if (value == null)
                CodeBlock.of(
                    "%T.ofEpochDay(0)",
                    LocalDate::class.asClassName()
                )
            else
                CodeBlock.of(
                    "%T.parse(%S, %T.ISO_DATE)",
                    LocalDate::class.asClassName(),
                    value.toString(),
                    DateTimeFormatter::class.asClassName()
                )
            LOCAL_DATE_TIME,
            ZONED_DATE_TIME,
            OFFSET_DATE_TIME -> {
                val clazz = when (type) {
                    LOCAL_DATE_TIME -> LocalDateTime::class.asClassName()
                    ZONED_DATE_TIME -> ZonedDateTime::class.asClassName()
                    OFFSET_DATE_TIME -> OffsetDateTime::class.asClassName()
                    else -> TODO("Support date type: $type")
                }
                if (value == null)
                    CodeBlock.of(
                        "%T.ofInstant(%T.EPOCH, %T.UTC)",
                        clazz,
                        Instant::class.asClassName(),
                        ZoneOffset::class.asClassName()
                    )
                else
                    CodeBlock.of(
                        "%T.parse(%S, %T.ISO_OFFSET_DATE_TIME)",
                        clazz::class.asClassName(),
                        value.toString(),
                        DateTimeFormatter::class.asClassName()
                    )
            }
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

fun generateKmfCode(
    jsonInput: Reader,
    generatedSourceDir: Path
) {
    val parsed = jsonInput.use { parseJson(it) }

    val fileSpec = FileSpec.builder(parsed.packageName, "Model").apply {
        addAnnotation(
            AnnotationSpec.builder(ClassName("", "Suppress"))
                .addMember("\"RedundantVisibilityModifier, USELESS_CAST\"")
                .build()
        )
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
                ?.let {
                    buildGenericClassName(
                        parsedRoot,
                        parsedClass.superClass
                    )
                }
                ?: KmfTypes.KMF_OBJECT
        )
        parsedClass.attributes.forEach { attrDesc ->
            buildPropertySpecs(
                parsedClass,
                parsedRoot,
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
                            buildAttributeValTypeClassName(parsedRoot, ad)
                        )
                    } else {
                        addStatement(
                            "${ad.internalPropName} = %T::class.java.cast(value)",
                            buildAttributeValTypeClassName(parsedRoot, ad)
                        )
                    }
                    endControlFlow()
                }

                beginControlFlow("else ->")
                addStatement("super.internalSetValue(attribute, value)")
                endControlFlow() // else ->
                endControlFlow() // when {
            }
        }.build())

        addType(buildKmfClassObject(parsedRoot, parsedClass))
    }.build()

private fun buildPropertySpecs(
    parsedClass: ClassDesc,
    parsedRoot: RootDesc,
    attrDesc: AttrDesc
): List<PropertySpec> = try {
    val propValueType = when (attrDesc.type) {
        AttrDescType.PROPERTY -> buildAttributeValTypeClassName(
            parsedRoot,
            attrDesc
        )
        AttrDescType.REFERENCE,
        AttrDescType.CHILD -> buildGenericClassName(
            parsedRoot,
            attrDesc.valueType
        )
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
                            .addStatement("ProtectedFunctions.setParent($internalPropName!!, null, null)")
                            .nextControlFlow("else")
                            .addStatement("ProtectedFunctions.setParent(value, this, KmfClass.${attrDesc.name})")
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

    listOfNotNull(internalProp, mainProp)
} catch (e: Exception) {
    throw Exception(
        "Failed to generate property ${parsedRoot.packageName}.${parsedClass.name}.${attrDesc.name}. ${e.message} (Full Attr Desc: $attrDesc)",
        e
    )
}

private fun buildKmfClassObject(parsedRoot: RootDesc, parsedClass: ClassDesc) =
    TypeSpec.objectBuilder("KmfClass").apply {
        superclass(KmfTypes.KMF_CLASS)
        superclassConstructorParameters += CodeBlock.of("${parsedClass.name}::class")
        superclassConstructorParameters += if (parsedClass.superClass == null)
            CodeBlock.of("null")
        else CodeBlock.of(
            "%T.KmfClass",
            buildGenericClassName(parsedRoot, parsedClass.superClass)
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
                        buildAttributeValTypeClassName(parsedRoot, ad),
                        KmfTypes.KMF_ATTRIBUTE_KIND,
                        buildGenericClassName(
                            parsedRoot,
                            parsedClass.name
                        )
                    )
                AttrDescMultiplicity.MANY ->
                    CodeBlock.of(
                        "%T(this, %T::class, %T.$kind, %T::${ad.name})",
                        attrType,
                        buildAttributeValTypeClassName(parsedRoot, ad),
                        KmfTypes.KMF_ATTRIBUTE_KIND,
                        buildGenericClassName(parsedRoot, parsedClass.name)
                    )
            }
            addProperty(
                PropertySpec.builder(ad.name, attrType)
                    .apply {
                        if (ad.name == "id") modifiers += KModifier.OVERRIDE
                    }
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

private fun buildAttributeValTypeClassName(
    parsedRoot: RootDesc,
    attrDesc: AttrDesc
) =
    PrimitiveTypes.getByName(attrDesc.valueType) ?: buildGenericClassName(
        parsedRoot,
        attrDesc.valueType
    )

private fun buildGenericClassName(
    parsedRoot: RootDesc,
    name: String
): ClassName {
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
