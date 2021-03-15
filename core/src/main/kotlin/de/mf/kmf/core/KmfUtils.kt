package de.mf.kmf.core

fun KmfObject.seqToRoot() = sequence<KmfObject> {
    var x: KmfObject? = this@seqToRoot
    while (x != null) {
        yield(x)
        x = x.parent
    }
}