package de.mf.kmf.kapt

import de.mf.kmf.api.KMF
import de.mf.kmf.kapt.types.PackageBuilder
import de.mf.kmf.kapt.types.createKMFEntity
import de.mf.kmf.kapt.types.isSubtypeOf
import org.eclipse.emf.ecore.EObject
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement

private val roundEnvThreadLocal = ThreadLocal<RoundEnvironment?>()

val roundEnvironment: RoundEnvironment
    get() = roundEnvThreadLocal.get()!!

private val messagerThreadLocal = ThreadLocal<Messager?>()

val messager: Messager
    get() = messagerThreadLocal.get()!!

private val processingEnvThreadLocal = ThreadLocal<ProcessingEnvironment?>()

val processEnvironment: ProcessingEnvironment
    get() = processingEnvThreadLocal.get()!!

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("de.mf.kmf.api.KMF")
class AnnotationProcessor : AbstractProcessor() {

    override fun process(
        annotations: MutableSet<out TypeElement>?,
        re: RoundEnvironment?
    ): Boolean {
        annotations ?: return false
        re ?: return false

        roundEnvThreadLocal.set(re)
        messagerThreadLocal.set(processingEnv.messager)
        processingEnvThreadLocal.set(processingEnv)

        try {
            logInfo("Run KMF Annotation Processor...")

            val elements = re
                .getElementsAnnotatedWith(KMF::class.java)
                ?.asSequence()
                ?.filterIsInstance<TypeElement>()
                ?.filter { it.kind == ElementKind.INTERFACE }
                ?.toList()
                ?.takeIf { !it.isEmpty() }
                ?: return false

            val package2entities = elements
                .map { elem ->
                    if (!elem.asType().isSubtypeOf(EObject::class.java)) {
                        logError("KMF-Interface ${elem.qualifiedName} doesn't implement ${EObject::class.java.canonicalName}.")
                        return false
                    }
                    val entity = createKMFEntity(elem)
                    if (entity == null) {
                        logError("Parsing KMF-Interface ${elem.qualifiedName} failed.")
                        return false
                    }
                    logInfo("Entity Found: ${entity.typeElement.qualifiedName}")
                    entity.properties.forEach { prop ->
                        logInfo("  - ${if (prop.setter == null) "val" else "var"} ${prop.name} : $prop")
                    }
                    entity
                }
                .asSequence()
                .filterNotNull()
                .groupBy { it.packageName }

            logInfo("Successfully parsed ${package2entities.values.sumBy { it.size }} KMF-interfaces in ${package2entities.size} packages.")

            package2entities.forEach { (packageName, entities) ->
                val lastDot = packageName.lastIndexOf('.')
                val name =
                    (if (lastDot == -1) packageName else packageName.substring(
                        lastDot + 1
                    ))
                        .toCharArray()
                        .let {
                            it[0] = it[0].toUpperCase()
                            String(it)
                        }

                val logStr = "| PROCESS PACKAGE $packageName |"
                logInfo("+${"-".repeat(logStr.length - 2)}+")
                logInfo(logStr)
                logInfo("+${"-".repeat(logStr.length - 2)}+")

                PackageBuilder(name, packageName, entities).buildPackage()
            }

            return true

        } finally {
            // clear thread locals
            roundEnvThreadLocal.set(null)
            messagerThreadLocal.set(null)
            processingEnvThreadLocal.set(null)
        }
    }
}