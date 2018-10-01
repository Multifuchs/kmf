package de.mf.kmf.api

class KMFException(
    msg: String,
    cause: Throwable? = null
) : RuntimeException(msg, cause)