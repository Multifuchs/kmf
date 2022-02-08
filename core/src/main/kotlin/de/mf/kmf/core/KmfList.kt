package de.mf.kmf.core

interface KmfList<T : Any> : MutableList<T> {
    fun move(from: Int, to: Int)

    override fun equals(other: Any?): Boolean
}