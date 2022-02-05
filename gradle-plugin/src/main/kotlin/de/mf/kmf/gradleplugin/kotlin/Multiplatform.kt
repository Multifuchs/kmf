/*
 * This code is from the great sqldelight project:
 * https://github.com/cashapp/sqldelight
 *
 * They also did a gradle plugin and did a great job handling the sources mess
 * in gradle.
 */

package de.mf.kmf.gradleplugin.kotlin

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation

fun Project.linkSqlite() {
  val extension = project.extensions.findByType(KotlinMultiplatformExtension ::class.java) ?: return
  extension.targets
    .flatMap { it.compilations }
    .filterIsInstance<KotlinNativeCompilation>()
    .forEach { compilationUnit ->
      compilationUnit.kotlinOptions.freeCompilerArgs += arrayOf("-linker-options", "-lsqlite3")
    }
}
