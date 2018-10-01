package de.mf.kmf.kapt.types

import de.mf.kmf.api.Contains
import de.mf.kmf.kapt.logError
import de.mf.kmf.kapt.processEnvironment
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.EObject
import java.math.BigDecimal
import java.math.BigInteger
import javax.lang.model.element.ExecutableElement
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
    val isContainment: Boolean,
    val defaultValueLiteral: String?
) {
}

data class KMFPropertyType(
    val typeMirror: TypeMirror,
    val isNullable: Boolean,
    /** Includes String, BigDecimal and BigInteger */
    val isPrimitive: Boolean,
    val isMany: Boolean
) {
}

private val getterPatter = "^(is|get)(\\p{javaUpperCase}.+)".toRegex()

fun createKMFEntity(typeElement: TypeElement): KMFEntity? {
    val packageName = processEnvironment
        .elementUtils.getPackageOf(typeElement)
        .qualifiedName.toString()

    val methods =
        typeElement.enclosedElements.filterIsInstance<ExecutableElement>()

    val properties =
        methods.asSequence()
            .filter { getterPatter.matches(it.simpleName) }
            .map { getter ->
                // get actual name
                val simplePropertyName = getterPatter
                    .find(getter.simpleName)
                    ?.groupValues
                    ?.takeIf { !it.isEmpty() }
                    ?.getOrNull(2)

                val propertyName =
                    if (simplePropertyName != null &&
                        getter.simpleName.startsWith("is")
                    ) {
                        "is$simplePropertyName"
                    } else
                        simplePropertyName

                if (propertyName == null) {
                    logError(
                        "Failed to find name of property for getter: ${typeElement.qualifiedName}.${getter.simpleName}",
                        getter
                    )
                    return@map null
                }

                val lcProperyName = propertyName.toCharArray().let {
                    it[0] = it[0].toLowerCase()
                    String(it)
                }

                // find corresponding setter
                val setter = methods
                    .firstOrNull {
                        it.simpleName.toString() == "set$propertyName"
                    }
                    ?: methods
                        .firstOrNull {
                            it.simpleName.toString() == "set$simplePropertyName"
                        }


                // process annotations
                val annotations =
                    getter.annotationMirrors
                        .asSequence()
                        .map { it.annotationType.asElement() as TypeElement }
                        .map { it.qualifiedName.toString() }
                        .toSet()

                // find return typeMirror
                val returnType = getter.returnType!!
                val propertyType = createKMFPropertyType(
                    annotations.contains("org.jetbrains.annotations.Nullable"),
                    returnType
                ) ?: run {
                    logError("Error detecting property typeMirror for ${typeElement.qualifiedName}.${getter.simpleName}")
                    return@map null
                }

                // find default value typeMirror
                val defaultValue = when {
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

                KMFProperty(
                    lcProperyName,
                    propertyType,
                    getter,
                    setter,
                    annotations.contains(Contains::class.qualifiedName!!),
                    defaultValue
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
    return KMFEntity(packageName, typeElement, properties)
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