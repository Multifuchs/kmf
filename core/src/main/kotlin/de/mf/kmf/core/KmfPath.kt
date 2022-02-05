package de.mf.kmf.core

val KmfObject.path: String
    get() = buildString {
        val pathToRoot = seqToRoot().toList()
        val rootObject = pathToRoot.last()
        val objId = idOrNull()

        

        var i = pathToRoot.lastIndex

        while (i >= 0) {
            append("/")
            i--
        }
    }