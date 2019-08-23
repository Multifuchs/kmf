package de.mf.kmf.codegen.impl

import com.beust.klaxon.JsonObject
import com.squareup.kotlinpoet.*
import de.mf.kmf.codegen.impl.ModelFeatureTypeKind.*
import org.eclipse.emf.ecore.*
import org.eclipse.emf.ecore.impl.EPackageImpl
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl

fun buildEPackage(mp: ModelPackage): FileSpec {
    require(!mp.isForeign)
    val json = mp.codeGen.jsons.first {
        it.requireString("nsURI") == mp.nsURI
    }
    val mpType = mp.poetEPackageType

    return FileSpec.builder(mpType.packageName, mpType.simpleName)
        .addType(TypeSpec.objectBuilder(mpType)
            .superclass(EPackageImpl::class)
            .addSuperclassConstructorParameter("%S", mp.nsURI)
            .addSuperclassConstructorParameter(mp.poetEFactoryType.simpleName)
            .addProperties(mp, json)
            .addFeatures(mp)
            .addInit(mp, json)
            .build())
        .build()
}

private fun TypeSpec.Builder.addProperties(mp: ModelPackage, json: JsonObject): TypeSpec.Builder {
    addProperty(PropertySpec.builder("eNAME", STRING)
        .initializer("%S", mp.nameIn(json)!!)
        .build()
    )
    addProperty(PropertySpec.builder("eNS_URI", STRING)
        .initializer("%S", mp.nsURI)
        .build()
    )
    addProperty(PropertySpec.builder("eNS_PREFIX", STRING)
        .initializer("%S", mp.nsURI
            .trimEnd('/')
            .split('/')
            .lastOrNull()
            ?.trim()
            ?.takeIf { it.all { it.isLetterOrDigit() } }
            ?.toLowerCase()
            ?: mp.nameIn(json)!!.toLowerCase())
        .build()
    )
    return this
}

private fun TypeSpec.Builder.addFeatures(mp: ModelPackage): TypeSpec.Builder {
    var idEClass = 0
    for (mt in mp.allModelTypes) {
        val eclass = mt.poetEClass
        val eclassID = mt.poetEClassID

        addProperty(PropertySpec.builder(eclassID.simpleName, INT, KModifier.CONST)
            .initializer(buildCodeBlock {
                addStatement((idEClass++).toString())
            })
            .build()
        )
        addProperty(PropertySpec.builder(eclass.simpleName, EClass::class)
            .build()
        )

        var idEFeature = 0
        for (f in mt.features) {
            val feat = f.poetFeature
            val featID = f.poetFeatureID
            val featType = when (f.typeKind) {
                PRIMITIVE -> EAttribute::class
                REFERENCE, CONTAINMENT -> EReference::class
            }

            addProperty(PropertySpec.builder(featID.simpleName, INT, KModifier.CONST)
                .initializer((idEFeature++).toString())
                .build()
            )
            addProperty(PropertySpec.builder(feat.simpleName, featType).build())
        }
        addProperty(PropertySpec.builder(mt.poetEClassID.simpleName.replace("__ID", "__FEATURE_COUNT"), INT)
            .initializer(idEFeature.toString())
            .build())
    }
    return this
}

private fun TypeSpec.Builder.addInit(mp: ModelPackage, json: JsonObject): TypeSpec.Builder {
    addInitializerBlock(buildCodeBlock {

        addStatement("name = eNAME")
        addStatement("nsPrefix = eNS_PREFIX")
        addStatement("nsURI = eNS_URI")

        for (mt in mp.allModelTypes) {
            val eclass = mt.poetEClass
            val eclassID = mt.poetEClassID

            addStatement("${mt.poetEClass.simpleName} = createEClass(${eclassID.simpleName})")

            for (f in mt.features) {
                val feat = f.poetFeature
                val featID = f.poetFeatureID
                val featType = when (f.typeKind) {
                    PRIMITIVE -> EAttribute::class
                    REFERENCE, CONTAINMENT -> EReference::class
                }

                val featureCreator = when (f.typeKind) {
                    PRIMITIVE -> "createEAttribute"
                    REFERENCE,
                    CONTAINMENT -> "createEReference"
                }
                addStatement("$featureCreator(${eclass.simpleName}, ${featID.simpleName})")
            }
        }

        for (mt in mp.allModelTypes) {
            for (f in mt.features) {
                val featureType = when (f.typeKind) {
                    PRIMITIVE -> EAttribute::class
                    REFERENCE,
                    CONTAINMENT -> EReference::class
                }
                addStatement("${f.poetFeature.simpleName} = ${mt.poetEClass.simpleName}.eStructuralFeatures[${f.poetFeatureID.simpleName}] as %T", featureType)
            }
        }

        for (mt in mp.allModelTypes) {
            addStatement("initEClass(%M, %T::class.java, %S,", mt.poetEClass, mt.poetEObjectTypeName, mt.name)
            indent()
            addStatement("!IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS)")
            unindent()
            for (f in mt.features) {
                when (f.typeKind) {
                    PRIMITIVE -> add("initEAttribute(")
                    REFERENCE, CONTAINMENT -> add("initEReference(")
                    else -> TODO()
                }
                indent()

                // 1st arg EStructuralFeature
                addStatement("%M, ", f.poetFeature)

                // 2nd Arg value type
                val primitiveTypeGetter = PrimitiveType.byKotlinName(f.typeName)?.eCoreTypeGetterName(f.isNullable)
                if (primitiveTypeGetter != null) {
                    addStatement("%T.eINSTANCE.${primitiveTypeGetter.simpleName}(), ", EcorePackage::class)
                } else {
                    val typeName = f.poetElementType
                    val typeMPckg = mp.codeGen.mPackages.first {
                        it.packageName == typeName.packageName
                    }
                    /*
    val pckg = EPackage.Registry.INSTANCE.getEPackage(EcorePackage.eNS_URI)
    val eclass = pckg.eClassifiers.filterIsInstance<EClass>()
                     */
                    if (typeMPckg.isForeign) {
                        // EcorePackage
                        add("%T.Registry.INSTANCE.getEPackage(%S).eClassifiers.filterIsInstance<%T>().first { it.name == %S} as %T, ",
                            EPackage::class, typeMPckg.nsURI, EClass::class, f.typeName, EClass::class)
                    } else {
                        val typeTypeModel = typeMPckg.allModelTypes.first {
                            it.name == typeName.simpleName
                        }
                        addStatement("%M, ", typeTypeModel.poetEClass)
                    }
                }

                // 3rd arg is null and is skipped by primitives
                when (f.typeKind) {
                    PRIMITIVE -> Unit
                    REFERENCE, CONTAINMENT -> addStatement("null, ")
                    else -> TODO()
                }

                // 4th arg is featureName
                addStatement("%S, ", f.featureName)

                // 5th arg is defaultValue
                val dvf = f.poetDefaultValueFieldName
                if (dvf == null) addStatement("null, ")
                else addStatement("%T.${dvf.simpleName}.toString(), ", dvf.enclosingClassName)

                // 6th and 7th are lower and upper bound
                when {
                    f.isMany -> addStatement("0, -1, ")
                    f.isNullable -> addStatement("0, 1, ")
                    else -> addStatement("1, 1, ")
                }

                // 8th is EObject javaclass
                addStatement("%T::class.java, ", mt.poetEObjectTypeName)

                // flags
                addStatement(when (f.typeKind) {
                    PRIMITIVE ->
                        "!IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, !IS_UNIQUE, !IS_DERIVED, IS_ORDERED"
                    REFERENCE ->
                        "!IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_COMPOSITE, IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED"
                    CONTAINMENT ->
                        "!IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED"
                })

                // close initEXXX function
                addStatement(")")
                unindent()
            }
        }

        addStatement("freeze()")
        addStatement("%T.Registry.INSTANCE.put(eNS_URI, this)", EPackage::class)
        addStatement("createResource(eNS_URI)")
    })
    return this
}