package de.mf.kmf.kapt.types

import com.google.common.base.CaseFormat
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import de.mf.kmf.api.KMFEObjectImpl
import de.mf.kmf.api.KMFEPackageImpl
import de.mf.kmf.kapt.logInfo
import de.mf.kmf.kapt.processEnvironment
import org.eclipse.emf.common.notify.NotificationChain
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.*
import org.eclipse.emf.ecore.impl.EFactoryImpl
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.reflect.KClass

class PackageBuilder(
    val name: String,
    val packageName: String,
    val entities: List<KMFEntity>
) {
    private val allProps = entities.flatMap { it.properties }

    private val foreignPackages = entities
        .asSequence()
        .flatMap { it.properties.asSequence() }
        .map { it.type.typeMirror }
        .filterNotNull()
        .map { processEnvironment.typeUtils.asElement(it) }
        .filterNotNull()
        .map {
            processEnvironment.elementUtils.getPackageOf(it)
                .qualifiedName.toString()
        }
        .distinct()
        .filter {
            !it.startsWith("java.") &&
                it != "org.eclipse.emf.common.util" &&
                it != packageName
        }
        .sorted()
        .toList()

    private val factoryClassName = "${name}Factory"
    private val packageClassName = "${name}Package"
    private val nsURI = packageName

    fun buildPackage() {

        foreignPackages.forEach { logInfo("Foreign referenced package: $it") }

        val fileSpec =
            FileSpec.builder(packageName, packageClassName).also { kt ->
                /*
                 * ADD Package
                 */
                kt.addType(
                    TypeSpec.objectBuilder(packageClassName)
                        .superclass(KMFEPackageImpl::class.java)
                        .also { pckObj ->
                            pckObj.addSuperclassConstructorParameter("\"$packageName\"")
                            pckObj.addSuperclassConstructorParameter(
                                factoryClassName
                            )
                                .also { builder ->
                                    entities.forEach { e ->
                                        builder.addSuperclassConstructorParameter(
                                            "${e.typeElement.qualifiedName}::class"
                                        )
                                    }
                                }
                            // add id's
                            entities.forEach { entity ->
                                pckObj.addConstant(
                                    entity.getIdConstantName(),
                                    Int::class,
                                    "getEClassId(${entity.typeElement.qualifiedName}::class)"
                                )
                                entity.properties.forEach { prop ->
                                    pckObj.addConstant(
                                        prop.getIdConstantName(),
                                        Int::class,
                                        "getEStructuralFeatureId(${entity.typeElement.qualifiedName}::${prop.name})"
                                    )
                                }
                            }

                            // add EClasses
                            entities.forEach { entity ->
                                pckObj.addProperty(
                                    PropertySpec.builder(
                                        entity.getEClassPckgPropertyName(),
                                        EClass::class.java
                                    )
                                        .initializer("getEClass(${entity.typeElement.qualifiedName}::class)")
                                        .build()
                                )

                                entity.properties.forEach { prop ->
                                    val propType =
                                        if (prop.type.isPrimitive) EAttribute::class.java else EReference::class.java
                                    pckObj.addProperty(
                                        PropertySpec.builder(
                                            prop.getFeaturePckgPropertyName(),
                                            propType
                                        )
                                            .initializer("get${propType.simpleName}(${entity.typeElement.qualifiedName}::${prop.name})")
                                            .build()
                                    )
                                }
                            }
                        }
                        .build())
                /*
                 * Add Entity-Implementations
                 */
                entities.forEach { entity ->
                    kt.addType(createImplementationSpec(entity))
                    kt.addFunction(createFactoryFunction(entity))
                }

                kt.addType(createFactorySpec())
            }.build()

        val generatedDir =
            Paths.get(processEnvironment.options["kapt.kotlin.generated"])
        Files.createDirectories(generatedDir)
        logInfo("Generated files directory: $generatedDir")
        fileSpec.writeTo(generatedDir)
    }

    private fun KMFEntity.getIdConstantName(): String =
        CaseFormat.UPPER_CAMEL.to(
            CaseFormat.UPPER_UNDERSCORE,
            typeElement.simpleName.toString()
        )

    private fun KMFEntity.getEClassPckgPropertyName(): String =
        typeElement.simpleName.toString()

    private fun KMFEntity.getImplClassName() =
        typeElement.simpleName.toString() + "Impl"

    private fun KMFProperty.getEntity() =
        entities.first { e -> e.properties.any { it === this } }

    private fun KMFProperty.getId(): Int = allProps.indexOf(this)
    private fun KMFProperty.getIdConstantName(): String =
        "${getEntity().getIdConstantName()}__${CaseFormat.LOWER_CAMEL.to(
            CaseFormat.UPPER_UNDERSCORE,
            this.name
        )}"

    private fun KMFProperty.getSpecialPurposeFunctionName(prefix: String): String =
        "${prefix}_${getImplPropertyName()}"

    private fun KMFProperty.getFeaturePckgPropertyName(): String =
        "${getEntity().getEClassPckgPropertyName()}_${this.name}"

    private fun KMFProperty.getImplPropertyName(): String =
        this.name

    private fun KMFProperty.getCodeFormattedDefaultValue(): String {
        return this.defaultValueLiteral?.let {
            if (this.type.typeMirror.rawType().isSame(
                    String::class.java
                )
            )
                "\"$it\""
            else it
        } ?: "null"
    }

    private fun TypeSpec.Builder.addConstant(
        name: String,
        type: KClass<*>,
        initializer: String
    ) {
        addProperty(
            PropertySpec.builder(
                name,
                type
            )
                .initializer(
                    if (type == String::class) "\"$initializer\""
                    else initializer
                )
                .build()
        )
    }

    private fun createImplementationSpec(entity: KMFEntity): TypeSpec =
        TypeSpec.classBuilder(entity.getImplClassName())
            .superclass(KMFEObjectImpl::class.java)
            .addSuperinterface(entity.typeElement.asClassName())
            .also { implSpec ->
                // override
                entity.properties.forEach { prop ->
                    val kotlinType =
                        prop.type.typeMirror.asTypeName().javaToKotlinType()

                    val typeMirror =
                        if (!prop.type.isMany) {
                            kotlinType
                        } else {
                            EList::class.asClassName()
                                .parameterizedBy(kotlinType)
                        }

                    val initializer = when {
                        !prop.type.isMany && prop.type.isPrimitive -> {
                            val defaultValue =
                                prop.getCodeFormattedDefaultValue()
                            "data($packageClassName.${prop.getIdConstantName()}, $defaultValue)"
                        }
                        else -> {
                            val delegateName = when {
                                !prop.type.isMany -> "reference"
                                prop.type.isPrimitive -> "dataList"
                                prop.isContainment -> "containmentList"
                                else -> "referenceList"
                            }
                            "$delegateName($packageClassName.${prop.getIdConstantName()})"
                        }
                    }
                    implSpec.addProperty(
                        PropertySpec.builder(
                            prop.getImplPropertyName(),
                            typeMirror
                                .let { if (prop.type.isNullable) it.asNullable() else it.asNonNullable() },
                            KModifier.OVERRIDE
                        )
                            .delegate(initializer)
                            .mutable(!prop.type.isMany)
                            .build()
                    )
                }

                // add property special functions
                entity.properties.forEach { prop ->
                    if (prop.isContainment && !prop.type.isMany) {
                        implSpec.addFunction(
                            FunSpec.builder(prop.getSpecialPurposeFunctionName("basicSet"))
                                .addParameter(
                                    "newValue",
                                    prop.type.typeMirror.asTypeName().asNullable()
                                )
                                .addParameter(
                                    "msgs",
                                    NotificationChain::class.java.asTypeName().asNullable()
                                )
                                .addCode("var res = msgs\n")
                                .addCode("val old = ${prop.getImplPropertyName()}\n")
                                .addCode("${prop.getImplPropertyName()} = newValue\n")
                                .addCode("if(eNotificationRequired()) {\n")
                                .addCode("    val n = org.eclipse.emf.ecore.impl.ENotificationImpl(this, org.eclipse.emf.common.notify.Notification.SET, $packageClassName.${prop.getIdConstantName()}, old, newValue)\n")
                                .addCode("    if(res == null) res = n\n")
                                .addCode("    else res.add(n)\n")
                                .addCode("}\n")
                                .addCode("return res\n")
                                .returns(NotificationChain::class.java.asTypeName().asNullable())
                                .build()
                        )
                    }
                }

                // add emf functions
                implSpec.addFunction(
                    FunSpec.builder("eStaticClass")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(EClass::class)
                        .addCode("return $packageClassName.${entity.getEClassPckgPropertyName()}\n")
                        .build()
                )
                implSpec.addFunction(
                    FunSpec.builder("eInverseRemove")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter(
                            "otherEnd",
                            InternalEObject::class.java.asTypeName().asNullable()
                        )
                        .addParameter("featureId", Int::class)
                        .addParameter(
                            "msgs",
                            NotificationChain::class.asTypeName().asNullable()
                        )
                        .returns(NotificationChain::class.java.asTypeName().asNullable())
                        .addCode("when (featureId) {\n")
                        .also { funSpec ->
                            entity.properties
                                .filter { it.isContainment }
                                .forEach { prop ->
                                    funSpec.addCode("    $packageClassName.${prop.getIdConstantName()} -> ")
                                    if (prop.type.isMany) {
                                        funSpec.addCode("return (${prop.getImplPropertyName()} as org.eclipse.emf.ecore.util.InternalEList<*>).basicRemove(otherEnd, msgs)\n")
                                    } else {
                                        funSpec.addCode(
                                            "return ${prop.getSpecialPurposeFunctionName(
                                                "basicSet"
                                            )}(null, msgs)\n"
                                        )
                                    }
                                }
                        }
                        .addCode("}\n")
                        .addCode("return super.eInverseRemove(otherEnd, featureId, msgs)")
                        .build()
                )

                implSpec.addFunction(
                    FunSpec.builder("eGet")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("featureId", Int::class.java)
                        .addParameter("resolve", Boolean::class.java)
                        .addParameter("coreType", Boolean::class.java)
                        .returns(Any::class.asTypeName().asNullable())
                        .addCode("return when (featureId) {\n")
                        .also { funSpec ->
                            entity.properties.forEach { prop ->
                                funSpec.addCode("    $packageClassName.${prop.getIdConstantName()} -> ${prop.getImplPropertyName()}\n")
                            }
                        }
                        .addCode("    else -> super.eGet(featureId, resolve, coreType)\n")
                        .addCode("}\n")
                        .build()
                )

                implSpec.addFunction(
                    FunSpec.builder("eSet")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("featureId", Int::class)
                        .addParameter(
                            "newValue",
                            Any::class.asTypeName().asNullable()
                        )
                        .addCode("when (featureId) {\n")
                        .also { funSpec ->
                            entity.properties.forEach { prop ->
                                funSpec.addCode("    $packageClassName.${prop.getIdConstantName()} -> ")
                                if (prop.type.isMany) {
                                    funSpec.addCode("{\n")
                                    funSpec.addCode("        ${prop.getImplPropertyName()}.clear()\n")
                                    funSpec.addCode("        ${prop.getImplPropertyName()}.addAll(newValue as kotlin.collections.Collection<${prop.type.typeMirror.asTypeName().javaToKotlinType()}>)\n")
                                    funSpec.addCode("    }\n")
                                } else
                                    funSpec.addCode("${prop.getImplPropertyName()} = newValue ${if (prop.type.isNullable) "as?" else "as"} ${prop.type.typeMirror.asTypeName().javaToKotlinType()}\n")
                            }
                        }
                        .addCode("    else -> super.eSet(featureId, newValue)\n")
                        .addCode("}\n")
                        .build()
                )

                implSpec.addFunction(FunSpec.builder("eUnset")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("featureId", Int::class)
                    .addCode("when (featureId) {\n")
                    .also { funSpec ->
                        entity.properties.forEach { prop ->
                            funSpec.addCode("    $packageClassName.${prop.getIdConstantName()} -> ")
                            when {
                                prop.type.isMany ->
                                    funSpec.addCode("${prop.getImplPropertyName()}.clear()\n")

                                else ->
                                    funSpec.addCode("${prop.getImplPropertyName()} = ${prop.getCodeFormattedDefaultValue()}\n")
                            }
                        }
                    }
                    .addCode("    else -> super.eUnset(featureId)\n")
                    .addCode("}\n")
                    .build())

                implSpec.addFunction(FunSpec.builder("eIsSet")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("featureId", Int::class)
                    .returns(Boolean::class)
                    .addCode("return when (featureId) {\n")
                    .also { funSpec ->
                        entity.properties.forEach { prop ->
                            funSpec.addCode("    $packageClassName.${prop.getIdConstantName()} -> ")
                            when {
                                prop.type.isMany ->
                                    funSpec.addCode("!${prop.getImplPropertyName()}.isEmpty()\n")

                                else ->
                                    funSpec.addCode("${prop.getImplPropertyName()} != ${prop.getCodeFormattedDefaultValue()}\n")
                            }
                        }
                    }
                    .addCode("    else -> super.eIsSet(featureId)\n")
                    .addCode("}\n")
                    .build())

            }
            .build()

    private fun createFactoryFunction(entity: KMFEntity): FunSpec =
        FunSpec.builder("create")
            .receiver(KClass::class.asClassName().parameterizedBy(entity.typeElement.asClassName()))
            .returns(entity.typeElement.asClassName())
            .addCode("return ${entity.getImplClassName()}()\n")
            .build()

    private fun createFactorySpec(): TypeSpec =
        TypeSpec.objectBuilder(factoryClassName)
            .superclass(EFactoryImpl::class.java)
            .also { facObj ->

                facObj.addFunction(
                    FunSpec.builder("create")
                        .returns(EObject::class)
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameter("eClass", EClass::class)
                        .addCode("return when (eClass.classifierID) {\n")
                        .also { funSpec ->
                            entities.forEach { entity ->
                                funSpec.addCode("\n    $packageClassName.${entity.getIdConstantName()} -> ${entity.getImplClassName()}()\n")
                            }
                        }
                        .addCode("    else -> throw kotlin.IllegalArgumentException(\"The class '\${eClass.name}' is not a valid classifier\")\n")
                        .addCode("}\n")
                        .build()
                )

                facObj.addInitializerBlock(CodeBlock.of("ePackage = $packageClassName\n"))
            }
            .build()
}

