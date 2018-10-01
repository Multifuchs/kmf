package de.mf.kmf.api

@Retention(AnnotationRetention.RUNTIME)
annotation class PackageReference(
    val packageName: String,
    val packageURI: String
)