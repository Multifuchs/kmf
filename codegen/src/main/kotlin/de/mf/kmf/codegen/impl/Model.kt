package de.mf.kmf.codegen.impl

import com.beust.klaxon.JsonObject
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.asClassName
import org.eclipse.emf.ecore.EDataType
import org.eclipse.emf.ecore.EcorePackage
import kotlin.reflect.KFunction1

data class ModelPackage(
    val codeGen: CodeGenerator,
    val nsURI: String,
    val packageName: String,
    val isForeign: Boolean
)

data class ModelType(
    val name: String,
    val definedInMPackage: ModelPackage,
    val features: List<ModelTypeFeature>
)

data class ModelTypeFeature(
    val featureName: String,
    val definedInMPackage: ModelPackage,
    /**
     * `null` indicates a primitive type
     */
    val typeFrom: ModelPackage?,
    val typeName: String,
    val typeKind: ModelFeatureTypeKind,
    val isMany: Boolean,
    val isNullable: Boolean,
    val defaultValueLiteral: String?
)

enum class ModelFeatureTypeKind {
    PRIMITIVE, REFERENCE, CONTAINMENT
}

enum class PrimitiveType {
    STRING,
    BYTE, SHORT, INT, LONG,
    FLOAT, DOUBLE,
    BOOLEAN,
    CHAR;

    val kotlinName = "${name.first()}${name.drop(1).toLowerCase()}"

    val defaultValueLiteral
        get() = when (this) {
            STRING -> ""
            BYTE, SHORT, INT, LONG -> "0"
            FLOAT -> "0.0f"
            DOUBLE -> "0.0"
            BOOLEAN -> "false"
            CHAR -> "'\\u0000'"
        }

    fun eCoreTypeGetterName(nullable: Boolean) = when (this) {
        STRING -> EcorePackage::getEString.memberName
        BYTE ->
            if (nullable) EcorePackage::getEByteObject.memberName
            else EcorePackage::getEByte.memberName
        SHORT ->
            if (nullable) EcorePackage::getEShortObject.memberName
            else EcorePackage::getEShort.memberName
        INT ->
            if (nullable) EcorePackage::getEIntegerObject.memberName
            else EcorePackage::getEInt.memberName
        LONG ->
            if (nullable) EcorePackage::getELongObject.memberName
            else EcorePackage::getELong.memberName
        FLOAT ->
            if (nullable) EcorePackage::getEFloatObject.memberName
            else EcorePackage::getEFloat.memberName
        DOUBLE ->
            if (nullable) EcorePackage::getEDoubleObject.memberName
            else EcorePackage::getEDouble.memberName
        BOOLEAN ->
            if (nullable) EcorePackage::getEBooleanObject.memberName
            else EcorePackage::getEBoolean.memberName
        CHAR ->
            if (nullable) EcorePackage::getECharacterObject.memberName
            else EcorePackage::getEChar.memberName
    }

    companion object {
        fun byKotlinName(kotlinName: String) =
            values().firstOrNull { it.kotlinName == kotlinName }

        private val KFunction1<EcorePackage, EDataType>.memberName
            get() = MemberName(EcorePackage::class.asClassName(), name)
    }
}

fun ModelPackage.nameIn(json: JsonObject) =
    if (json.string("nsURI") == nsURI) json.requireString("name")
    else {
        json.obj("uses")
            ?.entries
            ?.asSequence()
            ?.map { (name, obj) ->
                if (obj is JsonObject && obj.string("nsURI") == nsURI)
                    name
                else
                    null
            }
            ?.firstOrNull()
    }

val ModelPackage.allModelTypes
    get() = codeGen.mTypes.filter { it.definedInMPackage === this }

val ModelTypeFeature.enclosingType
    get() = definedInMPackage.allModelTypes.first { modelType ->
        modelType.features.any { it === this }
    }