package de.mf.kmf.gradleplugin

import de.mf.kmf.codegen.generateKmfCode
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.extension
import kotlin.io.path.isReadable
import kotlin.io.path.reader

abstract class KmfCodegenTask : SourceTask() {

    @get:OutputDirectory
    var outputDirectory: File? = null

    @Input
    val projectName: Property<String> =
        project.objects.property(String::class.java)

    @Input
    val sourceName: Property<String> =
        project.objects.property(String::class.java)

    @Input
    lateinit var sourceDirectories: List<File>

    @ExperimentalPathApi
    @TaskAction
    fun runCodegen() {
        val outputDir = outputDirectory?.toPath()
            ?: throw Exception("KMF Task failed: not output directory")
        source.visit { fileVisitDetails ->
            val file = fileVisitDetails.file.toPath()
                .takeIf { it.isReadable() && it.extension == "json" }
                ?: return@visit
            try {
                generateKmfCode(file.reader().buffered(), outputDir)
            } catch (e: Exception) {
                throw Exception("Failed to parse json: $file\n${e.message}", e)
            }
        }
    }
}