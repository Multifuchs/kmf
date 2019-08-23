package de.mf.kmf.codegen.impl

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import de.mf.kmf.codegen.impl.ModelFeatureTypeKind.*
import org.eclipse.emf.common.notify.Notification
import org.eclipse.emf.common.notify.NotificationChain
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.InternalEObject
import org.eclipse.emf.ecore.impl.ENotificationImpl
import org.eclipse.emf.ecore.impl.EObjectImpl
import org.eclipse.emf.ecore.util.EDataTypeEList
import org.eclipse.emf.ecore.util.EObjectContainmentEList
import org.eclipse.emf.ecore.util.EObjectResolvingEList

fun buildEObject(mt: ModelType) = FileSpec
    .builder(mt.definedInMPackage.packageName, mt.name)
    .addType(TypeSpec.classBuilder(mt.poetEObjectTypeName)
        .superclass(EObjectImpl::class)
        .addProperties(mt)
        .addDefaultValues(mt)
        .addOverrides(mt)
        .build()
    )
    .build()

private fun TypeSpec.Builder.addProperties(mt: ModelType): TypeSpec.Builder {
    // add shadowed properties
    for (f in mt.features) {
        val shadowedName = f.poetShadowedMemberName
        if (shadowedName != null) {
            addProperty(PropertySpec.builder(
                shadowedName.simpleName,
                f.poetType,
                KModifier.PRIVATE)
                .mutable(true)
                .initializer("null")
                .build()
            )
            when (f.typeKind) {
                CONTAINMENT -> {
                    addFunction(FunSpec.builder(f.poetBasicSet.simpleName)
                        .returns(NotificationChain::class.asTypeName()
                            .copy(nullable = true)
                        )
                        .addParameter("newValue", f.poetType)
                        .addParameter("msgs", NotificationChain::class.asTypeName()
                            .copy(nullable = true)
                        )
                        .addCode(buildCodeBlock {
                            addStatement("var result : %T = msgs", NotificationChain::class.asTypeName().copy(nullable = true))
                            addStatement("val oldValue = ${f.poetShadowedMemberName!!.simpleName}")
                            addStatement("${f.poetShadowedMemberName!!.simpleName} = newValue")
                            beginControlFlow("if (eNotificationRequired())")
                            addStatement("val notification = %T(this, %T.SET, %M, oldValue, newValue)", ENotificationImpl::class, Notification::class, f.poetFeatureID)
                            beginControlFlow("if (result == null)")
                            addStatement("result = notification")
                            nextControlFlow("else")
                            addStatement("result.add(notification)")
                            endControlFlow()
                            endControlFlow()
                            addStatement("return result")
                        })
                        .build()
                    )
                }
                REFERENCE -> {
                    addFunction(FunSpec.builder(f.poetBasicGet.simpleName)
                        .returns(f.poetType)
                        .addCode(buildCodeBlock {
                            addStatement("return ${f.poetShadowedMemberName!!.simpleName}")
                        })
                        .build()
                    )
                }
                else -> TODO()
            }
        }
    }

    // add regular properties
    for (f in mt.features) {
        val propSpec = PropertySpec.builder(
            f.poetMemberName.simpleName,
            f.poetType
        )

        if (f.isMany) {
            propSpec.mutable(false)
            propSpec.initializer(buildCodeBlock {
                when (f.typeKind) {
                    PRIMITIVE ->
                        add("%T<${f.typeName}>(${f.typeName}::class.java, this, %M)", EDataTypeEList::class, f.poetFeatureID)
                    REFERENCE ->
                        add("%T<${f.typeName}>(%T::class.java, this, %M)", EObjectResolvingEList::class, (f.poetType as ParameterizedTypeName).typeArguments.first(), f.poetFeatureID)
                    CONTAINMENT ->
                        add("%T<${f.typeName}>(%T::class.java, this, %M)", EObjectContainmentEList::class, (f.poetType as ParameterizedTypeName).typeArguments.first(), f.poetFeatureID)
                }
            })
        } else { // !isMany
            propSpec.mutable(true)
            when (f.typeKind) {
                PRIMITIVE -> {
                    propSpec.initializer(buildCodeBlock {
                        val field = f.poetDefaultValueFieldName
                        if (field != null)
                            addStatement("${mt.poetDefValObjectName.simpleName}.${field.simpleName}")
                        else
                            addStatement("null")
                    })
                    propSpec.setter(FunSpec.setterBuilder()
                        .addParameter("newValue", f.poetType)
                        .addCode(buildCodeBlock {
                            addStatement("val oldValue = field")
                            addStatement("field = newValue")
                            beginControlFlow("if (eNotificationRequired())")
                            addStatement("eNotify(%T(this, %T.SET, %M, oldValue, newValue))", ENotificationImpl::class, Notification::class, f.poetFeatureID)
                            endControlFlow()
                        })
                        .build()
                    )
                }
                REFERENCE -> {
                    propSpec.getter(FunSpec.getterBuilder()
                        .addCode(buildCodeBlock {
                            beginControlFlow("if (${f.poetShadowedMemberName!!.simpleName} != null && ${f.poetShadowedMemberName!!.simpleName}!!.eIsProxy())")
                            addStatement("val oldValue = ${f.poetShadowedMemberName!!.simpleName} as %T", InternalEObject::class)
                            addStatement("${f.poetShadowedMemberName!!.simpleName} = eResolveProxy(oldValue) as %T", f.poetType)
                            beginControlFlow("if (${f.poetShadowedMemberName!!.simpleName} !== oldValue)")
                            beginControlFlow("if (eNotificationRequired())")
                            addStatement("eNotify(%T(this, %T.RESOLVE, %M, oldValue, ${f.poetShadowedMemberName!!.simpleName}))", ENotificationImpl::class, Notification::class, f.poetFeatureID)
                            endControlFlow()
                            endControlFlow()
                            endControlFlow()
                            addStatement("return ${f.poetShadowedMemberName!!.simpleName}")
                        })
                        .build()
                    )
                    propSpec.setter(FunSpec.setterBuilder()
                        .addParameter("newValue", f.poetType)
                        .addCode(buildCodeBlock {
                            addStatement("val oldValue = ${f.poetShadowedMemberName!!.simpleName}")
                            addStatement("${f.poetShadowedMemberName!!.simpleName} = newValue")
                            beginControlFlow("if (eNotificationRequired())")
                            addStatement("eNotify(%T(this, %T.SET, %M, oldValue, ${f.poetShadowedMemberName!!.simpleName}))", ENotificationImpl::class, Notification::class, f.poetFeatureID)
                            endControlFlow()
                        })
                        .build()
                    )
                }
                CONTAINMENT -> {
                    propSpec.getter(FunSpec.getterBuilder()
                        .addCode(buildCodeBlock {
                            addStatement("return ${f.poetShadowedMemberName!!.simpleName}")
                        })
                        .build()
                    )
                    propSpec.setter(FunSpec.setterBuilder()
                        .addParameter("newValue", f.poetType)
                        .addCode(buildCodeBlock {
                            beginControlFlow("if (newValue !== ${f.poetShadowedMemberName!!.simpleName})")
                            addStatement("var msgs: %T? = null", NotificationChain::class)
                            beginControlFlow("if (${f.poetShadowedMemberName!!.simpleName} != null)")
                            addStatement("msgs = (${f.poetShadowedMemberName!!.simpleName} as %T).eInverseRemove(this, EOPPOSITE_FEATURE_BASE - %M, null, msgs)", InternalEObject::class, f.poetFeatureID)
                            endControlFlow()
                            beginControlFlow("if (newValue != null)")
                            addStatement("msgs = (newValue as %T).eInverseAdd(this, EOPPOSITE_FEATURE_BASE - %M, null, msgs)", InternalEObject::class, f.poetFeatureID)
                            endControlFlow()
                            addStatement("msgs = ${f.poetBasicSet.simpleName}(newValue, msgs)")
                            addStatement("msgs?.dispatch()")
                            nextControlFlow("else if (eNotificationRequired())")
                            addStatement("eNotify(%T(this, %T.SET, %M, newValue, newValue))", ENotificationImpl::class, Notification::class, f.poetFeatureID)
                            endControlFlow()
                        })
                        .build()
                    )
                }
            }
        }

        addProperty(propSpec.build())
    }
    return this
}

private fun TypeSpec.Builder.addDefaultValues(mt: ModelType): TypeSpec.Builder {
    data class DefaultValueProp(
        val feature: ModelTypeFeature,
        val propName: MemberName,
        val literal: String
    )

    val defValProps = mt.features.asSequence()
        .map {
            val propName = it.poetDefaultValueFieldName
            if (propName == null) null
            else DefaultValueProp(
                it,
                propName,
                when {
                    it.defaultValueLiteral != null ->
                        if (it.typeName == "String") "\"${it.defaultValueLiteral}\""
                        else it.defaultValueLiteral
                    it.isNullable -> "null"
                    it.typeName == "String" -> "\"\""
                    else -> PrimitiveType.byKotlinName(it.typeName)!!.defaultValueLiteral
                }
            )

        }
        .filterNotNull()
        .toList()
        .takeIf { it.isNotEmpty() }
        ?: return this

    val defValObj = TypeSpec.objectBuilder(mt.poetDefValObjectName)

    for (devValProp in defValProps) {
        defValObj.addProperty(
            PropertySpec.builder(
                devValProp.propName.simpleName,
                devValProp.feature.poetType
            )
                .initializer(buildCodeBlock {
                    addStatement("%L", devValProp.literal)
                })
                .build()
        )
    }

    addType(defValObj.build())
    return this
}

private fun TypeSpec.Builder.addOverrides(mt: ModelType): TypeSpec.Builder {
    /* eStaticClass() */
    addFunction(FunSpec.builder("eStaticClass")
        .addModifiers(KModifier.PROTECTED, KModifier.OVERRIDE)
        .returns(EClass::class)
        .addCode(buildCodeBlock {
            addStatement("return %M", mt.poetEClass)
        })
        .build()
    )

    /* eInverseRemove */
    val eInvRmFeatures = mt.features.filter {
        !it.isMany && it.typeKind == CONTAINMENT
    }
    if (eInvRmFeatures.isNotEmpty()) {
        addFunction(FunSpec.builder("eInverseRemove")
            .addModifiers(KModifier.OVERRIDE)
            .returns(NotificationChain::class.asTypeName().copy(nullable = true))
            .addParameter("otherEnd", InternalEObject::class.asTypeName().copy(nullable = true))
            .addParameter("featureId", INT)
            .addParameter("msgs", NotificationChain::class.asTypeName().copy(nullable = true))
            .addCode(buildCodeBlock {
                beginControlFlow("return when (featureId)")
                for (irf in eInvRmFeatures) {
                    addStatement("%M -> ${irf.poetBasicSet.simpleName}(null, msgs)", irf.poetFeatureID)
                }
                addStatement("else -> super.eInverseRemove(otherEnd, featureId, msgs)")
                endControlFlow()

            })
            .build()
        )
    }

    /* eGet */
    if (mt.features.isNotEmpty()) {
        addFunction(FunSpec.builder("eGet")
            .addModifiers(KModifier.OVERRIDE)
            .returns(ANY.copy(nullable = true))
            .addParameter("featureID", INT)
            .addParameter("resolve", BOOLEAN)
            .addParameter("coreType", BOOLEAN)
            .addCode(buildCodeBlock {
                beginControlFlow("return when (featureID)")
                for (f in mt.features) {
                    beginControlFlow("%M ->", f.poetFeatureID)
                    if (!f.isMany && f.typeKind == REFERENCE) {
                        addStatement("if (resolve) ${f.poetMemberName.simpleName}")
                        addStatement("else ${f.poetBasicGet.simpleName}()")
                    } else {
                        addStatement(f.poetMemberName.simpleName)
                    }
                    endControlFlow()
                }
                addStatement("else -> super.eGet(featureID, resolve, coreType)")
                endControlFlow()
            })
            .build()
        )
    }

    /* eSet */
    if (mt.features.isNotEmpty()) {
        addFunction(FunSpec.builder("eSet")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("featureID", INT)
            .addParameter("newValue", ANY.copy(nullable = true))
            .addCode(buildCodeBlock {
                beginControlFlow("when (featureID)")
                for (f in mt.features) {
                    beginControlFlow("%M ->", f.poetFeatureID)
                    if (f.isMany) {
                        addStatement("${f.poetMemberName.simpleName}.clear()")
                        addStatement("${f.poetMemberName.simpleName}.addAll(newValue as %T)", COLLECTION.parameterizedBy((f.poetType as ParameterizedTypeName).typeArguments.first()))
                    } else {
                        addStatement("${f.poetMemberName.simpleName} = (newValue as %T)", f.poetType)
                    }
                    endControlFlow()
                }
                addStatement("else -> super.eSet(featureID, newValue)")
                endControlFlow()
            })
            .build()
        )
    }

    /* eIsSet */
    if (mt.features.isNotEmpty()) {
        addFunction(FunSpec.builder("eIsSet")
            .addModifiers(KModifier.OVERRIDE)
            .returns(BOOLEAN)
            .addParameter("featureID", INT)
            .addCode(buildCodeBlock {
                beginControlFlow("return when (featureID)")
                for (f in mt.features) {
                    beginControlFlow("%M ->", f.poetFeatureID)
                    val name = f.poetShadowedMemberName?.simpleName ?: f.poetMemberName.simpleName
                    if (f.isMany) {
                        addStatement("$name != null && !$name.isEmpty()")
                    } else {
                        val defVal = f.poetDefaultValueFieldName
                        if (defVal == null)
                            addStatement("$name != null", name)
                        else
                            addStatement("$name != ${mt.poetDefValObjectName.simpleName}.${defVal.simpleName}")
                    }
                    endControlFlow()
                }
                addStatement("else -> super.eIsSet(featureID)")
                endControlFlow()
            })
            .build()
        )
    }

    /* eUnset */
    if (mt.features.isNotEmpty()) {
        addFunction(FunSpec.builder("eUnset")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("featureID", INT)
            .addCode(buildCodeBlock {
                beginControlFlow("when (featureID)")
                for (f in mt.features) {
                    beginControlFlow("%M ->", f.poetFeatureID)
                    if (f.isMany) {
                        addStatement("${f.poetMemberName.simpleName}.clear()")
                    } else {
                        val defVal = f.poetDefaultValueFieldName
                        if (defVal == null)
                            addStatement("${f.poetMemberName.simpleName} = null")
                        else
                            addStatement("${f.poetMemberName.simpleName} = ${mt.poetDefValObjectName.simpleName}.${defVal.simpleName}")
                    }
                    endControlFlow()
                }
                addStatement("else -> super.eIsSet(featureID)")
                endControlFlow()
            })
            .build()
        )
    }

    return this
}