package de.mf.kmf.codegen.impl

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.eclipse.emf.common.util.DiagnosticChain
import org.eclipse.emf.ecore.*

private val ModelType.poetValidateFunName get() = "validate$name"

private val ModelTypeFeature.poetValidateFunName
    get() = "validate${enclosingType.name}_$featureName"

fun buildEValidator(mp: ModelPackage): FileSpec {
    val vType = mp.poetEValidatorType
    return FileSpec.builder(vType.packageName, vType.simpleName)
        .addType(TypeSpec.classBuilder(vType)
            .addModifiers(KModifier.ABSTRACT)
            .addSuperinterface(EValidator::class)
            .addMaps(mp)
            .addOverrideFun(mp)
            .addMTypeFun(mp)
            .build()
        )
        .build()
}

private fun TypeSpec.Builder.addOverrideFun(
    mp: ModelPackage
): TypeSpec.Builder {

    /*
    boolean validate(
        EObject eObject,
        DiagnosticChain diagnostics,
        Map<Object, Object> context);
     */
    addFunction(FunSpec.builder("validate")
        .addModifiers(KModifier.OPEN)
        .addModifiers(KModifier.OVERRIDE)
        .returns(BOOLEAN)
        .addParameter("eObject", EObject::class.nullable(true))
        .addParameter("diagnostics", DiagnosticChain::class.nullable(true))
        .addParameter("context",
            MUTABLE_MAP.parameterizedBy(ANY.nullable(), ANY.nullable()).nullable(true)
        )
        .addCode(buildCodeBlock {
            addStatement("return validate(eObject?.eClass(), eObject, diagnostics, context)")
        })
        .build()
    )

    val reqNotNull = MemberName("kotlin", "requireNotNull")

    /*
    boolean validate(
        EClass eClass,
        EObject eObject,
        DiagnosticChain diagnostics,
        Map<Object, Object> context);
     */
    addFunction(FunSpec.builder("validate")
        .addModifiers(KModifier.OPEN)
        .addModifiers(KModifier.OVERRIDE)
        .returns(BOOLEAN)
        .addParameter("eClass", EClass::class.nullable())
        .addParameter("eObject", EObject::class.nullable())
        .addParameter("diagnostics", DiagnosticChain::class.nullable())
        .addParameter("context",
            MUTABLE_MAP.parameterizedBy(ANY.nullable(), ANY.nullable()).nullable(true)
        )
        .addCode(buildCodeBlock {
            addStatement("%M(eClass)", reqNotNull)
            addStatement("%M(eObject)", reqNotNull)
            addStatement("%M(diagnostics)", reqNotNull)
            beginControlFlow("when (eClass)")
            for (mt in mp.allModelTypes) {
                addStatement("%T.${mt.poetEClass.simpleName} ->",
                    mp.poetEPackageType
                )
                indent()
                addStatement("${mt.poetValidateFunName}(eObject as %T, diagnostics)",
                    mt.poetEObjectTypeName

                )
                addStatement("")
                unindent()
            }
            endControlFlow()
            addStatement("return true")
        })
        .build()
    )

    /*
    boolean validate(
        EDataType eDataType,
        Object value,
        DiagnosticChain diagnostics,
        Map<Object, Object> context);
     */
    addFunction(FunSpec.builder("validate")
        .addModifiers(KModifier.OPEN)
        .addModifiers(KModifier.OVERRIDE)
        .returns(BOOLEAN)
        .addParameter("eDataType", EDataType::class.nullable())
        .addParameter("value", ANY.nullable())
        .addParameter("diagnostics", DiagnosticChain::class.nullable())
        .addParameter("context", MUTABLE_MAP.parameterizedBy(ANY.nullable(), ANY.nullable()).nullable())
        .addCode(buildCodeBlock {
            addStatement("return true")
        })
        .build()
    )


    return this
}

private fun TypeSpec.Builder.addMTypeFun(
    mp: ModelPackage
): TypeSpec.Builder {

    for (mt in mp.allModelTypes) {
        addFunction(FunSpec.builder(mt.poetValidateFunName)
            .addModifiers(KModifier.OPEN)
            .addParameter("eObj", mt.poetEObjectTypeName)
            .addParameter("diag", DiagnosticChain::class)
            .addCode(buildCodeBlock {
                for (f in mt.features) {
                    addStatement("${f.poetValidateFunName}(")
                    indent()
                    addStatement("eObj,")
                    addStatement("eObj.${f.poetMemberName.simpleName},")
                    addStatement("diag")
                    unindent()
                    addStatement(")")
                    addStatement("")
                }
            })
            .build()
        )
        for (f in mt.features) {
            addFunction(FunSpec.builder(f.poetValidateFunName)
                .addModifiers(KModifier.OPEN)
                .addParameter("eObj", mt.poetEObjectTypeName)
                .addParameter("value", f.poetType)
                .addParameter("diag", DiagnosticChain::class)
                .build()
            )
        }
    }

    return this
}

private fun TypeSpec.Builder.addMaps(
    mp: ModelPackage
): TypeSpec.Builder {

    addProperty(PropertySpec.builder(
        "ECLASS_TO_VALIDATOR",
        MAP.parameterizedBy(EClass::class.nullable(false))
            .plusParameter(LambdaTypeName.get(
                parameters = *arrayOf(EObject::class.asTypeName(),
                    DiagnosticChain::class.asTypeName()),
                returnType = UNIT))
    )
        .initializer(buildCodeBlock {
            addStatement("mapOf(")
            indent()
            for (mt in mp.allModelTypes) {
                addStatement("%T.${mt.poetEClass.simpleName} to { eObj, diag ->", mp.poetEPackageType)
                indent()
                addStatement("${mt.poetValidateFunName}(eObj as %T, diag) ", mt.poetEObjectTypeName)
                unindent()
                addStatement(if (mt !== mp.allModelTypes.last()) "}," else "}")
            }
            unindent()
            addStatement(")")
        })
        .build()
    )

    addProperty(PropertySpec.builder(
        "EFEATURE_TO_VALIDATOR",
        MAP.parameterizedBy(EStructuralFeature::class.nullable(false))
            .plusParameter(LambdaTypeName.get(
                parameters = *arrayOf(EObject::class.asTypeName(),
                    ANY.nullable(),
                    DiagnosticChain::class.asTypeName()),
                returnType = UNIT))
    )
        .initializer(buildCodeBlock {
            addStatement("mapOf(")
            indent()
            for (mt in mp.allModelTypes) {
                for (f in mt.features) {
                    addStatement("%T.${f.poetFeature.simpleName} to { eObj, value, diag ->", mp.poetEPackageType)
                    indent()
                    addStatement("${f.poetValidateFunName}(eObj as %T, value as %T, diag)", mt.poetEObjectTypeName, f.poetType)
                    unindent()
                    if (mt === mp.allModelTypes.last() && f === mt.features.last()) addStatement("}")
                    else addStatement("},")
                }
            }
            unindent()
            addStatement(")")
        })
        .build()
    )

    return this
}