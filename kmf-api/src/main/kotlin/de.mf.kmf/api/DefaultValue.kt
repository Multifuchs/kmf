package de.mf.kmf.api

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class DefaultValue(val value: String)