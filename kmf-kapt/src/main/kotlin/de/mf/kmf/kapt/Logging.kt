package de.mf.kmf.kapt

import javax.tools.Diagnostic

fun logInfo(
    text: String,
    element: javax.lang.model.element.Element? = null,
    annotationMirror: javax.lang.model.element.AnnotationMirror? = null,
    annotationValue: javax.lang.model.element.AnnotationValue? = null
) {
    messager.printMessage(
        Diagnostic.Kind.NOTE,
        text,
        element,
        annotationMirror,
        annotationValue
    )
}

fun logWarn(
    text: String,
    element: javax.lang.model.element.Element? = null,
    annotationMirror: javax.lang.model.element.AnnotationMirror? = null,
    annotationValue: javax.lang.model.element.AnnotationValue? = null
) {
    messager.printMessage(
        Diagnostic.Kind.MANDATORY_WARNING,
        text,
        element,
        annotationMirror,
        annotationValue
    )
}

fun logError(
    text: String,
    element: javax.lang.model.element.Element? = null,
    annotationMirror: javax.lang.model.element.AnnotationMirror? = null,
    annotationValue: javax.lang.model.element.AnnotationValue? = null
) {
    messager.printMessage(
        Diagnostic.Kind.ERROR,
        text,
        element,
        annotationMirror,
        annotationValue
    )
}