package de.mf.kmf.codegen

import de.mf.kmf.codegen.impl.CodeGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

class Runner() {
    var logger: Logger? = null
    var modelFiles: MutableList<File> = mutableListOf<File>()
    var buildDir: File? = null

    fun exec() {
        check(modelFiles.isNotEmpty()) { "Runner.modelFiles must not be empty" }
        modelFiles.forEach {
            check(it.isFile && it.extension == "json") {
                "Each entry in Runner.modelFiles must be an existing json file: $it"
            }
        }
        checkNotNull(buildDir) { "Runner.buildDir must not be null" }
        check(!buildDir!!.exists() || buildDir!!.isDirectory) {
            "Runner.buildDir must be a valid directory"
        }

        CodeGenerator(logger, modelFiles, buildDir!!).run()
    }
}