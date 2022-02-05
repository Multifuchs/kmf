package de.mf.kmf.gradleplugin

import de.mf.kmf.gradleplugin.kotlin.sources
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

open class KmfCodegenPlugin : Plugin<Project> {

    private val existsKotlinPlugin = AtomicBoolean(false)
    private lateinit var project: Project

    override fun apply(project: Project) {
        this.project = project

        project.logger.warn("KMF Apply project")

        val kotlinPluginHandler =
            { _: Plugin<*> -> existsKotlinPlugin.set(true) }
        project.plugins.withId(
            "org.jetbrains.kotlin.multiplatform",
            kotlinPluginHandler
        )
        project.plugins.withId(
            "org.jetbrains.kotlin.android",
            kotlinPluginHandler
        )
        project.plugins.withId("org.jetbrains.kotlin.jvm", kotlinPluginHandler)
        project.plugins.withId("kotlin2js", kotlinPluginHandler)

//        kotlin.run {
//            val sources = project.sources()
//            val common = sources
//                .singleOrNull { it.sourceSets.singleOrNull() == "commonMain" }
//
//            for (source in sources) {
//                val genSrcDir = File(project.buildDir, "kmf${source.name.capitalize()}")
//                val relGenSrcDir =
//                    genSrcDir.toRelativeString(project.projectDir)
//
//                if (common == null || source === common)
//                    source.sourceDirectorySet.srcDir(relGenSrcDir)
//            }
//        }

        project.afterEvaluate {
            check(existsKotlinPlugin.get()) {
                "KMF plugin de.mf.kmf.codegen must be applied after a org.jetbrains.kotlin.{multiplatform|android|jvm}"
            }

            project.tasks.register(GROUP_TASK_ID) {
                it.group = GROUP
                it.description = "Generation of kotlin code out of kmf-json"
            }

            val sources = project.sources()
            val common = sources
                .singleOrNull { it.sourceSets.singleOrNull() == "commonMain" }

            for (source in sources) {
                project.logger.warn("KMF source: ${source}")
                val genSrcDir = File(project.buildDir, "kmf${source.name.capitalize()}")
                val relGenSrcDir =
                    genSrcDir.toRelativeString(project.projectDir)

                if (common == null || source === common)
                    source.sourceDirectorySet.srcDir(relGenSrcDir)

                val srcDirs = source.sourceSets.map {
                    File(project.projectDir, "src/$it/kmf")
                }
                val projectSrcDirs = project.files(*srcDirs.toTypedArray())
                val task = project.tasks.register(
                    "kmf${source.name.capitalize()}Codegen",
                    KmfCodegenTask::class.java
                ) {
                    it.projectName.set(project.name)
                    it.sourceName.set(source.name)
                    it.sourceDirectories = srcDirs
                    it.outputDirectory = genSrcDir
                    it.source(projectSrcDirs)
                    it.include("**${File.separatorChar}*.json")
                    it.group = GROUP
                    it.description = "Generate ${source.name} Kotlin classes"
                }

                project.tasks.named(GROUP_TASK_ID).configure {
                    it.dependsOn(task)
                }

                source.registerTaskDependency(task)
            }
        }
    }

    companion object {
        val GROUP = "kmfCodegen"
        val GROUP_TASK_ID = "kmfAllCodegen"
    }
}