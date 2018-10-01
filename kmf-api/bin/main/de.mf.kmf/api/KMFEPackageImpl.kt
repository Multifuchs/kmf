package de.mf.kmf.api

import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.*
import org.eclipse.emf.ecore.impl.EPackageImpl
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

open class KMFEPackageImpl(
    val packageName: String,
    eFactoryInstance: EFactory,
    vararg val kmfTypes: KClass<out EObject>
) : EPackageImpl("http://kmf/$packageName", eFactoryInstance) {

    init {
        val prefix = packageName.split('.').last()
        nsPrefix = prefix
        nsURI = "http://kmf/${packageName.replace('.', '/')}"
        name = prefix
    }

    private val type2eClass: Map<KClass<out EObject>, EClass>
    private val prop2eFeature: Map<KProperty1<*, *>, EStructuralFeature>

    init {
        val packageName2ePackage = kmfTypes
            .flatMap {
                it.findAnnotation<KMF>()?.packageReferences?.toList()
                    ?: emptyList()
            }
            .map {
                val resolvedPackage =
                    EPackage.Registry.INSTANCE.getEPackage(it.packageURI)
                        ?: throw KMFException(
                            "Resolving EPackage " +
                                "'${it.packageURI}' failed. That package " +
                                "might not be initialized yet. Also note, " +
                                "that KMF doesn't support cyclic package " +
                                "dependencies and requires you to initialize " +
                                "all involved EPackages in the right order " +
                                "before using them."
                        )
                it.packageName to resolvedPackage
            }
            .let {
                listOf(
                    packageName to this,
                    "org.eclipse.emf.ecore" to EcorePackage.eINSTANCE
                ) + it
            }
            .toMap()


        // Initialize simple dependencies
        EcorePackage.eINSTANCE.eClass
        packageName2ePackage.values.forEach { it.eClass() }

        // create contents
        var curClassifierId = 0
        var curFeatureId = 0

        type2eClass = kmfTypes.map {
            val eClass = createEClass(curClassifierId++)
            it to eClass
        }.toMap()

        prop2eFeature = kmfTypes.flatMap { type ->
            val eClass = type2eClass[type]!!
            type.declaredMemberProperties.map { prop ->
                val featureId = curFeatureId++

                if (prop.isPrimitive()) createEAttribute(eClass, featureId)
                else createEReference(eClass, featureId)

                val eFeature =
                    eClass.eStructuralFeatures.first { it.featureID == featureId }

                prop to eFeature
            }
        }.toMap()

        // initialize EClasses
        kmfTypes.forEach { type ->
            val eClass = type2eClass[type]!!
            val kmf = type.findAnnotation<KMF>()
            initEClass(
                eClass,
                type.java,
                type.simpleName!!,
                kmf?.isAbstract ?: false,
                kmf?.isInterface ?: false,
                true
            )
        }

        // initialize EFeatures
        kmfTypes.forEach { type ->
            type.declaredMemberProperties.forEach { prop ->
                val eFeature = prop2eFeature[prop]!!
                if (prop.isPrimitive()) {
                    initEAttribute(
                        eFeature as EAttribute,
                        prop.getDataType().toPrimitiveEDataType(prop.returnType.isMarkedNullable),
                        prop.name,
                        null,
                        0,
                        if (prop.isMany()) -1 else 1,
                        type.java,
                        false,
                        false,
                        true,
                        false,
                        false,
                        false,
                        false,
                        true
                    )
                } else {
                    val dataType = prop.getDataType()
                    val dataTypePackageName = dataType.qualifiedName!!
                        .split('.')
                        .toMutableList()
                        .also { if (it.size > 0) it.removeAt(it.lastIndex) }
                        .joinToString(".")
                    val dataTypeEPackage =
                        packageName2ePackage[dataTypePackageName]
                            ?: throw KMFException(
                                "Cannot find EPackage for type '$dataType'." +
                                    "This type is required to initialize the " +
                                    "property '$prop'."
                            )
                    val dataTypeEClass =
                        dataTypeEPackage.getEClassifier(
                            dataType.simpleName ?: ""
                        )
                            ?: throw KMFException(
                                "Cannot find EClass for type '$dataType' in " +
                                    "EPackage ${dataTypeEPackage.nsURI}."
                            )

                    initEReference(
                        eFeature as EReference,
                        dataTypeEClass,
                        null,
                        prop.name,
                        null,
                        0,
                        if (prop.isMany()) -1 else 1,
                        type.java,
                        false,
                        false,
                        true,
                        prop.isContainment(),
                        !prop.isContainment(),
                        false,
                        true,
                        false,
                        true
                    )
                }
            }
        }

        // Mark meta-data to indicate it can't be changed
        freeze()

        // register this EPackage
        EPackage.Registry.INSTANCE[nsURI] = this
    }

    protected fun getEClassId(kmfType: KClass<out EObject>): Int {
        return type2eClass[kmfType]?.classifierID
            ?: throw KMFException("No classifierId found for type $kmfType")
    }

    protected fun getEClass(kmfType: KClass<out EObject>): EClass {
        return type2eClass[kmfType]
            ?: throw KMFException("No EClass found for type $kmfType")
    }

    protected fun getEStructuralFeatureId(prop: KProperty1<*, *>): Int {
        return prop2eFeature[prop]?.featureID
            ?: throw KMFException("No featureId found for property $prop")
    }

    protected fun getEAttribute(prop: KProperty1<*, *>): EAttribute {
        return prop2eFeature[prop] as? EAttribute
            ?: throw KMFException("No EAttribute found for property $prop")
    }

    protected fun getEReference(prop: KProperty1<*, *>): EReference {
        return prop2eFeature[prop] as? EReference
            ?: throw KMFException("No EReference found for property $prop")
    }
}

private fun KProperty1<*, *>.getDataType(): KClass<*> =
    if ((returnType.classifier as KClass<*>).isSubclassOf(EList::class))
        (returnType.arguments[0].type!!.classifier as KClass<*>)
    else
        returnType.classifier as KClass<*>

private fun KClass<*>.isPrimitive() = when {
    this == String::class -> true
    this == BigDecimal::class -> true
    this == BigInteger::class -> true
    this.javaPrimitiveType != null -> true
    else -> false
}

private fun KProperty1<*, *>.isContainment() =
    (getter.findAnnotation<Contains>() != null)

private fun KProperty1<*, *>.isMany() =
    (returnType.classifier as KClass<*>).isSubclassOf(EList::class)

private fun KProperty1<*, *>.isPrimitive() =
    getDataType().isPrimitive()

private fun KClass<*>.toPrimitiveEDataType(isMarkedNullable: Boolean): EDataType {
    val ecore = EcorePackage.eINSTANCE!!
    return when {
        this == String::class -> ecore.eString
        this == Int::class ->
            if (isMarkedNullable) ecore.eIntegerObject
            else ecore.eInt
        this == Long::class ->
            if (isMarkedNullable) ecore.eLongObject
            else ecore.eLong
        this == Short::class ->
            if (isMarkedNullable) ecore.eShortObject
            else ecore.eShort
        this == Float::class ->
            if (isMarkedNullable) ecore.eFloatObject
            else ecore.eFloat
        this == Double::class ->
            if (isMarkedNullable) ecore.eDoubleObject
            else ecore.eDouble
        this == Char::class ->
            if (isMarkedNullable) ecore.eCharacterObject
            else ecore.eChar
        this == Boolean::class ->
            if (isMarkedNullable) ecore.eBooleanObject
            else ecore.eBoolean
        this == BigDecimal::class ->
            ecore.eBigDecimal
        this == BigInteger::class ->
            ecore.eBigInteger
        else -> TODO("Support primitive datatype ${this}")
    }
}