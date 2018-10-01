package de.mf.kmf.kapt.types

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import de.mf.kmf.kapt.processEnvironment
import javax.lang.model.element.Element
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import kotlin.reflect.jvm.internal.impl.builtins.jvm.JavaToKotlinClassMap
import kotlin.reflect.jvm.internal.impl.name.FqName

fun Class<*>.asType() = try {
    processEnvironment.elementUtils.getTypeElement(canonicalName).asType()!!
} catch (e: Exception) {
    throw Exception(
        "Failed to convert ${this} AKA ${canonicalName} to type mirror",
        e
    )
}

fun TypeMirror.isSubtypeOf(other: TypeMirror) =
    processEnvironment.typeUtils.isSubtype(this, other)

fun TypeMirror.rawType() =
    (this as? DeclaredType)?.let {
        processEnvironment.typeUtils.erasure(it)
    } ?: this

fun TypeMirror.isSubtypeOf(other: Class<*>) = isSubtypeOf(other.asType())

fun TypeMirror.isSame(other: TypeMirror, raw: Boolean = false) =
    if (raw) processEnvironment.typeUtils.isSameType(
        this.rawType(),
        other.rawType()
    )
    else processEnvironment.typeUtils.isSameType(this, other)

fun TypeMirror.isSame(other: Class<*>, raw: Boolean = false) =
    isSame(other.asType(), raw)

fun Element.javaToKotlinType(): TypeName =
    asType().asTypeName().javaToKotlinType()

fun TypeName.javaToKotlinType(): TypeName {
    return if (this is ParameterizedTypeName) {
        (rawType.javaToKotlinType() as ClassName).parameterizedBy(*typeArguments.map { it.javaToKotlinType() }.toTypedArray())
    } else {
        val className =
            JavaToKotlinClassMap.INSTANCE.mapJavaToKotlin(FqName(toString()))
                ?.asSingleFqName()?.asString()

        return if (className == null) {
            this
        } else {
            ClassName.bestGuess(className)
        }
    }
}