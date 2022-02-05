package de.mf.kmf.gradleplugin

import java.util.*

fun String.capitalize(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }