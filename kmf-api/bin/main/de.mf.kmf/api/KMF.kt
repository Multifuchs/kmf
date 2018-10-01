package de.mf.kmf.api

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class KMF(
    val packageReferences: Array<PackageReference> = [],
    val isInterface: Boolean = false,
    val isAbstract: Boolean = false
)