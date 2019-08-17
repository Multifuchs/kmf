package de.mf.kmf.codegen.impl

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import org.slf4j.Logger
import java.io.File
import java.lang.IllegalArgumentException

class CodeGenerator(
    private val log: Logger?,
    private val modelFiles: List<File>,
    private val buildDir: File
) {
    val jsons = mutableListOf<JsonObject>()
    val mPackages = mutableListOf<ModelPackage>()
    val mTypes = mutableListOf<ModelType>()

    /*
     * Parsed Data
     */
    fun run() {
        if (log != null && log.isInfoEnabled) {
            log.info("generate code for the following models:")
            modelFiles.forEach { log.info("- $it") }
        }
        if (buildDir.isDirectory) {
            buildDir.deleteRecursively()
        }
        check(buildDir.mkdirs() || buildDir.isDirectory) {
            "creating build directory $buildDir failed"
        }

        parseJsons()
        createPackages()
        validatePackages()
        createTypes()

        mPackages.forEach {
            buildEFactory(it).writeTo(buildDir)
            buildEPackage(it).writeTo(buildDir)
        }
        mTypes.forEach {
            buildEObject(it).writeTo(buildDir)
        }
    }

    /**
     * Populates the [jsons] list.
     */
    private fun parseJsons() {
        for (f in modelFiles) {
            val json = f.bufferedReader(Charsets.UTF_8).use {
                Parser.default().parse(it) as JsonObject
            }
            addPathFields(json, f.name)
            jsons += json
        }
    }

    /**
     * Populates the [mPackages] list.
     */
    private fun createPackages() {
        /*
         * add all which are directly
         */
        for (j in jsons) {
            mPackages += ModelPackage(
                this,
                j.requireString("nsURI"),
                j.requireString("package"),
                false
            )
        }
        /*
         * add all used
         */
        for (j in jsons) {
            val uses = j.obj("uses") ?: continue
            for ((name, used) in uses) {
                if (name.isJsonSpecialField || used !is JsonObject) continue
                val nsURI = used.requireString("nsURI")

                val newPckg = ModelPackage(
                    this,
                    used.requireString("nsURI"),
                    used.requireString("package"),
                    true
                )

                val existing = mPackages.firstOrNull {
                    it.nsURI == nsURI
                }

                when {
                    existing == null -> mPackages += newPckg

                    // referenced package is referenced by another model
                    // but with different properties
                    existing.isForeign -> check(existing == newPckg) {
                        "package ${used.path} is used in another model with " +
                            "different properties. Make sure, that the field " +
                            "'package' is the same."
                    }

                    // referenced package is build by codegenerator, too.
                    // java-package must not be specified
                    used.containsKey("package") ->
                        log?.warn("${used.path}.package is ignored since it " +
                            "is already defined in another model file"
                        )
                }
            }
        } // foreach in jsons
    }

    private fun validatePackages() {
        val nsURISet = mutableSetOf<String>()
        val packageSet = mutableSetOf<String>()

        for (p in mPackages) {
            check(nsURISet.add(p.nsURI)) {
                "nsURI ${p.nsURI} must be unique"
            }
            check(packageSet.add(p.packageName)) {
                "package ${p.packageName} must be unique"
            }
        }
    }

    private fun createTypes() {
        for (j in jsons) {
            val nameToJson = j.obj("types")
                ?.entries
                ?.asSequence()
                ?.map { (tName, tObj) ->
                    if (tName.isJsonSpecialField) return@map null
                    val trimmedName = tName.trim()
                    if (tObj is JsonObject && trimmedName.isNotBlank())
                        tName to tObj
                    else
                        null
                }
                ?.filterNotNull()
                ?.toList()
                ?: emptyList()

            check(!nameToJson.isEmpty()) {
                "no types defined in model ${j.path}"
            }

            val modelMPackage = getMainMPackage(j)!!
            for ((name, typeJson) in nameToJson) {
                if (name.isJsonSpecialField) continue
                val features = typeJson.asSequence()
                    .filter { !it.key.isJsonSpecialField }
                    .map { (fn, ftl) ->
                        check(ftl is String) {
                            "string-value expected for ${typeJson.path}.$fn"
                        }
                        createTypeFeature(j, typeJson, fn, ftl)
                    }
                    .toList()
                mTypes += ModelType(name, modelMPackage, features)
            }
        }
    }

    private fun createTypeFeature(
        model: JsonObject,
        type: JsonObject,
        featureName: String,
        featureTypeLiteral: String
    ): ModelTypeFeature {
        /*
         * find out where it is defined
         */
        val definedIn = findMPackage(model.requireString("nsURI"))
        checkNotNull(definedIn)

        /*
         * parse literal
         */
        val matcher = TYPE_FEATURE_PATTERN.matcher(featureTypeLiteral)
        check(matcher.find()) {
            "can't parse the type definition of ${type.path}.$featureName"
        }
        val refTypeName = matcher.group("refType") ?: null
        val pckgName = matcher.group("pckg") ?: null
        val typeName = matcher.group("name")!!
        val isList = matcher.group("list") != null
        val isNullableSpecified = matcher.group("nullable") != null
        val specifiedDefaultValueLiteral = matcher.group("defaultValue") ?: null

        /*
         * resolve type
         */
        val mainMPackage = getMainMPackage(model)!!
        val mainMPackageName = mainMPackage.nameIn(model)!!

        val isPrimitive = pckgName == null
            && PrimitiveType.byKotlinName(typeName) != null

        if (isPrimitive && refTypeName != null) {
            log?.warn("reference type '$refTypeName' is ignored for" +
                "primitive types: ${type.path}.$featureName")
        }

        if ((!isPrimitive || isList) && isNullableSpecified) {
            log?.warn("nullability is ignored for non-primitive and non-lists " +
                "features: ${type.path}.$featureName")
        }

        val isNullable = when {
            isPrimitive -> isNullableSpecified
            isList -> false
            else -> true
        }

        if ((isPrimitive || isList) && specifiedDefaultValueLiteral != null) {
            log?.warn("default value is ignored for non-primitive features " +
                "and lists: ${type.path}.$featureName")
        }

        val defaultValueLiteral = when {
            isPrimitive && !isList -> specifiedDefaultValueLiteral
            else -> null
        }

        val mPackage =
            if (isPrimitive)
                null
            else if (pckgName == null || pckgName == mainMPackageName)
                mainMPackage
            else
                checkNotNull(findMPackageByName(model, pckgName)) {
                    "can't resolve model-package with name '$pckgName' " +
                        "for ${type.path}.$featureName"
                }

        val typeKind = when {
            isPrimitive -> ModelFeatureTypeKind.PRIMITIVE
            refTypeName == "ref" -> ModelFeatureTypeKind.REFERENCE
            refTypeName == "contains" -> ModelFeatureTypeKind.CONTAINMENT
            else -> throw IllegalArgumentException("non-primitive property " +
                "must be either a reference ('ref') or containment " +
                "('contains'). Examples: 'ref $featureTypeLiteral' or " +
                "'contains $featureTypeLiteral': ${type.path}.$featureName")
        }

        return ModelTypeFeature(
            featureName, definedIn, mPackage, typeName,
            typeKind, isList, isNullable,
            defaultValueLiteral
        )
    }

    private fun findMPackage(nsURI: String) =
        mPackages.firstOrNull { it.nsURI == nsURI }

    private fun findMPackageByName(model: JsonObject, name: String) =
        mPackages.firstOrNull { it.nameIn(model) == name }

    private fun getMainMPackage(jsonModel: JsonObject) =
        findMPackage(jsonModel.requireString("nsURI"))

    private companion object {
        private val TYPE_FEATURE_PATTERN = (
            "(?:(?<refType>(?:ref|contains)) )?" +
                "(?:(?<pckg>\\p{Alnum}+)\\.)?(?<name>\\p{Alnum}+)" +
                "(?<nullable>\\?)?" +
                "(?<list>\\[\\])?" +
                "(?:=(?<defaultValue>.+))?"
            ).toPattern()
    }
}