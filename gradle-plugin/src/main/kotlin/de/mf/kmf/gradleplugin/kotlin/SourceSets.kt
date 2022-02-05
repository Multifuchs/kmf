/*
 * This code is from the great sqldelight project:
 * https://github.com/cashapp/sqldelight
 *
 * They also did a gradle plugin and did a great job handling the sources mess
 * in gradle.
 */

package de.mf.kmf.gradleplugin.kotlin

import com.android.build.gradle.api.AndroidSourceSet
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.HasConvention
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.plugin.KOTLIN_DSL_NAME
import org.jetbrains.kotlin.gradle.plugin.KOTLIN_JS_DSL_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

internal val AndroidSourceSet.kotlin: SourceDirectorySet?
  get() = kotlinSourceSet

internal val SourceSet.kotlin: SourceDirectorySet?
  get() = kotlinSourceSet

// Copied from kotlin plugin
private val Any.kotlinSourceSet: SourceDirectorySet?
  get() {
    val convention = (getConvention(KOTLIN_DSL_NAME) ?: getConvention(KOTLIN_JS_DSL_NAME)) ?: return null
    val kotlinSourceSetIface =
      convention.javaClass.interfaces.find { it.name == KotlinSourceSet::class.qualifiedName }
    val getKotlin = kotlinSourceSetIface?.methods?.find { it.name == "getKotlin" } ?: return null
    return getKotlin(convention) as? SourceDirectorySet
  }

private fun Any.getConvention(name: String): Any? =
  (this as HasConvention).convention.plugins[name]
