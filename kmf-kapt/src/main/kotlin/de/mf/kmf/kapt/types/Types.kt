package de.mf.kmf.kapt.types

import de.mf.kmf.api.Contains
import de.mf.kmf.api.DefaultValue
import de.mf.kmf.kapt.logError
import de.mf.kmf.kapt.logWarn
import de.mf.kmf.kapt.processEnvironment
import kotlinx.metadata.*
import kotlinx.metadata.jvm.*
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.EObject
import java.math.BigDecimal
import java.math.BigInteger
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.PrimitiveType
import javax.lang.model.type.TypeMirror

data class KMFEntity(
    val packageName: String,
    val typeElement: TypeElement,
    val properties: List<KMFProperty>
)

data class KMFProperty(
    val name: String,
    val type: KMFPropertyType,
    val getter: ExecutableElement,
    val setter: ExecutableElement?,
    val annotationHolder: Element?,
    val isContainment: Boolean,
    val defaultValueLiteral: String?
)

data class KMFPropertyType(
    val typeMirror: TypeMirror,
    val isNullable: Boolean,
    /** Includes String, BigDecimal and BigInteger */
    val isPrimitive: Boolean,
    val isMany: Boolean
)

fun createKMFEntity(typeElement: TypeElement): KMFEntity? {

    val classInfo = gatherClassInfo(typeElement) ?: run {
        logError("Failed to process ${typeElement.qualifiedName}.")
        return null
    }

    val methods =
        typeElement.enclosedElements.filterIsInstance<ExecutableElement>()

    val properties = classInfo.properties.asSequence()
        .map { propInfo ->
            val getter =
                methods.first {
                    it.simpleName.toString() == propInfo.getterName
                }
            val setter =
                propInfo.setterName?.let { setterName ->
                    methods.first { it.simpleName.toString() == setterName }
                }

            val propType =
                createKMFPropertyType(propInfo.isNullable, getter.returnType)
            if (propType == null) {
                logError("Failed to gather type information for ${typeElement.qualifiedName}.${propInfo.name}")
                return@map null
            }

            val isContainment = propInfo.annotationHolder
                ?.getAnnotation(Contains::class.java) != null

            val defaultValueLiteral = propInfo.annotationHolder
                ?.getAnnotation(DefaultValue::class.java)
                ?.value
                ?: getDefaultValueForType(propType)

            KMFProperty(
                propInfo.name,
                propType,
                getter,
                setter,
                propInfo.annotationHolder,
                isContainment,
                defaultValueLiteral
            )
        }
        .filter { prop ->
                // validation stuff
                prop ?: return@filter true
                val qualName =
                    "${typeElement.qualifiedName}.${prop.getter.simpleName}"
                val error = when {
                    prop.isContainment && prop.type.isPrimitive ->
                        "Containment property $qualName must not be primitive."
                    prop.type.isMany && prop.setter != null ->
                        "Many property $qualName must be read-only (val)."
                    !prop.type.isMany && prop.setter == null ->
                        "Non-list property $qualName must be mutable (var)."
                    !prop.type.isMany && !prop.type.isPrimitive && !prop.type.isNullable ->
                        "Reference (non-primitive) typeMirror property $qualName must be mutable and nullable."
                    else -> null
                }
                if (error != null) {
                    logError(error)
                    false
                } else
                    true
            }
            .filterNotNull()
            .toList()

    return KMFEntity(classInfo.packageName, typeElement, properties)
}

private fun createKMFPropertyType(
    isNullable: Boolean,
    returnType: TypeMirror
): KMFPropertyType? = when (returnType) {
    is PrimitiveType -> {
        KMFPropertyType(returnType, false, true, false)
    }
    is DeclaredType -> {
        val unboxed = try {
            processEnvironment.typeUtils.unboxedType(returnType)
        } catch (e: IllegalArgumentException) {
            null
        }

        when {
            returnType.isSame(BigInteger::class.java) ||
                returnType.isSame(BigDecimal::class.java) ||
                returnType.isSame(String::class.java) ||
                unboxed != null ->
                // primitive
                KMFPropertyType(
                    returnType,
                    isNullable,
                    true,
                    false
                )

            returnType.isSame(EList::class.java, raw = true) -> {
                // many typeMirror
                val elementType =
                    createKMFPropertyType(false, returnType.typeArguments[0])
                if (elementType == null) {
                    logError("Lists element typeMirror doesn't meet requirements or can't be processed.")
                    null
                } else if (elementType.isNullable || elementType.isMany) {
                    logError("Lists element typeMirror must not be null nor another EList.")
                    null
                } else
                    KMFPropertyType(
                        elementType.typeMirror,
                        false,
                        elementType.isPrimitive,
                        true
                    )
            }

            returnType.isSubtypeOf(EObject::class.java) ->
                // reference
                KMFPropertyType(
                    returnType,
                    isNullable,
                    false,
                    false
                )

            else -> {
                logError("Type of property must be either primitive, EList or implement EObject")
                null
            }
        }
    }
    else -> null
}

private fun gatherClassInfo(typeElement: TypeElement): KMFClassInfo? {
    val metadataAnnotation =
        typeElement.getAnnotation(kotlin.Metadata::class.java)
    if (metadataAnnotation == null) {
        logError("Missing @kotlin.Metadata Annotation: ${typeElement.qualifiedName}")
        return null
    }

    val classMeta = KotlinClassHeader(
        metadataAnnotation.k,
        metadataAnnotation.mv,
        metadataAnnotation.bv,
        metadataAnnotation.d1,
        metadataAnnotation.d2,
        metadataAnnotation.xs,
        metadataAnnotation.pn,
        metadataAnnotation.xi
    ).let {
        KotlinClassMetadata.read(it) as? KotlinClassMetadata.Class
    }
    if (classMeta == null) {
        logError("@kotlin.Metadata can't be parsed.")
        return null
    }

    val defaultImpls = typeElement.enclosedElements.asSequence()
        .filterIsInstance<TypeElement>()
        .filter {
            it.simpleName.toString() == "DefaultImpls" &&
                it.modifiers.containsAll(
                    listOf(Modifier.FINAL, Modifier.PUBLIC)
                )
        }
        .firstOrNull()

    val allProperties = mutableListOf<KMFPropertyInfo?>()

    var className: ClassName? = null

    classMeta.accept(object : KmClassVisitor() {

        private var propName: String = ""
        private var isNullable: Boolean? = null
        private var getterName: String? = null
        private var setterName: String? = null
        private var syntheticMethodForAnnotationsName: String? = null

        override fun visit(flags: Flags, name: ClassName) {
            if (!Flag.Class.IS_INTERFACE(flags))
                logError("${typeElement.qualifiedName} is expected to be an interface.")
            else
                className = name
        }

        override fun visitProperty(
            flags: Flags,
            name: String,
            getterFlags: Flags,
            setterFlags: Flags
        ): KmPropertyVisitor? {
            if (!Flag.IS_PUBLIC(flags)) return null

            propName = name
            getterName = null
            setterName = null
            syntheticMethodForAnnotationsName = null

            return object : KmPropertyVisitor() {
                override fun visitReturnType(flags: Flags): KmTypeVisitor? {
                    isNullable = Flag.Type.IS_NULLABLE(flags)
                    return null
                }

                override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? {
                    return if (type.klass != JvmPropertyExtensionVisitor::class) null
                    else object : JvmPropertyExtensionVisitor() {
                        override fun visit(
                            fieldDesc: JvmFieldSignature?,
                            getterDesc: JvmMethodSignature?,
                            setterDesc: JvmMethodSignature?
                        ) {
                            getterName = getterDesc?.name
                            setterName = setterDesc?.name
                        }

                        override fun visitSyntheticMethodForAnnotations(desc: JvmMethodSignature?) {
                            syntheticMethodForAnnotationsName = desc?.name
                        }
                    }
                }

                override fun visitEnd() {
                    allProperties += if (propName.isBlank() || getterName == null || isNullable == null) {
                        logError("Property name or getter is missing.")
                        null
                    } else {
                        val annotationHolder =
                            if (syntheticMethodForAnnotationsName == null) null
                            else defaultImpls?.enclosedElements
                                ?.asSequence()
                                ?.filterIsInstance<ExecutableElement>()
                                ?.filter { it.simpleName.toString() == "$syntheticMethodForAnnotationsName" }
                                ?.firstOrNull()
                                ?: run {
                                    logWarn("Property ${typeElement.qualifiedName}.$name is expected to have annotations, but no annotation holder exists.")
                                    null
                                }
                        KMFPropertyInfo(
                            propName,
                            isNullable!!,
                            getterName!!,
                            setterName,
                            annotationHolder
                        )
                    }
                }
            }
        }
    })

    val allValidProperties = allProperties.filterNotNull()
        .takeIf { it.size == allProperties.size }

    if (className == null || allValidProperties == null) return null

    val (packageName, simpleClassName) = className!!.split('/')
        .let {
            it.asSequence().take(it.size - 1).joinToString(".") to it.last()
        }

    return KMFClassInfo(simpleClassName, packageName, allValidProperties)
}

/** Contains relevant raw info from parsed TypeElement */
private data class KMFClassInfo(
    val name: String,
    val packageName: String,
    val properties: List<KMFPropertyInfo>
)

private data class KMFPropertyInfo(
    val name: String,
    val isNullable: Boolean,
    val getterName: String,
    val setterName: String?,
    val annotationHolder: Element?
)

private fun getDefaultValueForType(propertyType: KMFPropertyType): String? =
    when {
        !propertyType.isNullable && propertyType.isPrimitive && !propertyType.isMany -> {
            val boxed =
                (propertyType.typeMirror as? PrimitiveType)?.let {
                    processEnvironment.typeUtils.boxedClass(it)
                        .asType()
                } ?: propertyType.typeMirror
            when {
                boxed.isSame(String::class.java) -> ""

                boxed.isSame(java.lang.Integer::class.java) ||
                    boxed.isSame(java.lang.Long::class.java) ||
                    boxed.isSame(java.lang.Short::class.java) ||
                    boxed.isSame(java.lang.Character::class.java) ||
                    boxed.isSame(BigInteger::class.java) -> "0"

                boxed.isSame(java.lang.Float::class.java) ||
                    boxed.isSame(java.lang.Double::class.java) ||
                    boxed.isSame(BigDecimal::class.java) -> "0.0"

                boxed.isSame(java.lang.Boolean::class.java) -> "false"

                else -> TODO("set default value for primitive typeMirror $boxed")
            }
        }
        else -> null
    }