package de.mf.kmf.codegen.impl

import com.google.common.base.CaseFormat.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import de.mf.kmf.codegen.impl.ModelFeatureTypeKind.*
import org.eclipse.emf.common.util.EList

/** de.example.ExamplePackage */
val ModelPackage.poetEPackageType: ClassName
    get() {
        val json = codeGen.jsons.first { it.requireString("nsURI") == nsURI }
        val name = LOWER_CAMEL.to(UPPER_CAMEL, nameIn(json)!!)
        return ClassName(packageName, name + "Package")
    }
/** de.example.ExampleFactory */
val ModelPackage.poetEFactoryType: ClassName
    get() {
        val json = codeGen.jsons.first { it.requireString("nsURI") == nsURI }
        val name = LOWER_CAMEL.to(UPPER_CAMEL, nameIn(json)!!)
        return ClassName(packageName, name + "Factory")
    }

/** de.example.Foo */
val ModelType.poetEObjectTypeName get() = ClassName(definedInMPackage.packageName, name)

/** de.example.Foo.Defaults */
val ModelType.poetDefValObjectName
    get() = ClassName(
        definedInMPackage.packageName,
        name,
        "Defaults"
    )

val ModelTypeFeature.poetElementType
    get() = ClassName(
        // use kotlin package for all primitives
        typeFrom?.packageName ?: "kotlin",
        typeName
    ).copy(nullable = isNullable)

/** kotlin.Int, de.example.Bar, EList<Bar> ... */
val ModelTypeFeature.poetType: TypeName
    get() {
        return if (isMany)
            EList::class.asClassName().parameterizedBy(poetElementType)
        else
            poetElementType
    }

/** de.example.Foo::bar */
val ModelTypeFeature.poetMemberName
    get() = MemberName(enclosingType.poetEObjectTypeName, featureName)

/** de.example.Foo::eBar */
val ModelTypeFeature.poetShadowedMemberName: MemberName?
    get() {
        if (isMany) return null
        if (typeKind != REFERENCE && typeKind != CONTAINMENT) return null
        return MemberName(
            enclosingType.poetEObjectTypeName,
            "e${LOWER_CAMEL.to(UPPER_CAMEL, featureName)}"
        )
    }

/** de.example.Foo::basicGetBar */
val ModelTypeFeature.poetBasicGet
    get() = MemberName(
        enclosingType.poetEObjectTypeName,
        "basicGet${LOWER_CAMEL.to(UPPER_CAMEL, featureName)}"
    )
/** de.example.Foo::basicGetBar */
val ModelTypeFeature.poetBasicSet
    get() = MemberName(
        enclosingType.poetEObjectTypeName,
        "basicSet${LOWER_CAMEL.to(UPPER_CAMEL, featureName)}"
    )

/** de.example.Foo.Defaults.BAR */
val ModelTypeFeature.poetDefaultValueFieldName
    get() =
        if (!isMany
            && typeKind == PRIMITIVE
            && (!isNullable || defaultValueLiteral != null)
        ) {
            MemberName(
                enclosingType.poetDefValObjectName,
                LOWER_CAMEL.to(UPPER_UNDERSCORE, featureName)
            )
        } else null

/** de.example.ExamplePackage::FOO__BAR */
val ModelTypeFeature.poetFeature: MemberName
    get() {
        val enclosing = enclosingType
        return MemberName(
            enclosing.definedInMPackage.poetEPackageType,
            buildString {
                append(UPPER_CAMEL.to(UPPER_UNDERSCORE, enclosing.name))
                append("__")
                append(LOWER_CAMEL.to(UPPER_UNDERSCORE, featureName))
            }
        )
    }

/** de.example.ExamplePackage::FOO__BAR__ID */
val ModelTypeFeature.poetFeatureID: MemberName
    get() {
        val f = poetFeature
        return MemberName(
            f.enclosingClassName!!,
            f.simpleName + "__ID"
        )
    }

/** de.example.ExamplePackage::FOO */
val ModelType.poetEClass: MemberName
    get() {
        return MemberName(
            definedInMPackage.poetEPackageType,
            UPPER_CAMEL.to(UPPER_UNDERSCORE, name)
        )
    }

/** de.example.ExamplePackage::FOO__ID */
val ModelType.poetEClassID: MemberName
    get() {
        val f = poetEClass
        return MemberName(
            f.enclosingClassName!!,
            f.simpleName + "__ID"
        )
    }

