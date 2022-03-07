package de.mf.kmf.observer

import de.mf.kmf.core.KmfAttrKind
import de.mf.kmf.core.KmfAttribute
import de.mf.kmf.core.KmfObject
import de.mf.kmf.core.kmfClass
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class KmfObjPath<R : KmfObject, D : KmfObject> private constructor(
    val rootType: KClass<R>,
    val destType: KClass<D>,
    val attributes: List<KmfAttribute.Unary>
) {

    val rootKmfClass = rootType.kmfClass
    val destKmfClass = destType.kmfClass

    fun <T : KmfObject> append(property: KProperty1<D, T?>): KmfObjPath<R, T> {
        val attr = requireNotNull(
            destKmfClass.allAttributes.firstOrNull { it.name == property.name }
        ) { "Property ${property.name} is not an attribute of ${destKmfClass.kClass}" }
        require(attr is KmfAttribute.Unary && (attr.kind == KmfAttrKind.CHILD || attr.kind == KmfAttrKind.REFERENCE)) {
            "Attribute $attr must be unary and either a child or reference attribute."
        }
        @Suppress("UNCHECKED_CAST")
        return KmfObjPath(rootType, attr.valueType as KClass<T>, attributes + attr)
    }

    fun resolve(obj: R): D? {
        require(obj.kmfClass.isSubclassOf(rootKmfClass)) {
            "obj must be a subclass of $rootKmfClass."
        }
        var cur: KmfObject = obj
        for (attr in attributes) {
            cur = (attr.get(cur) as KmfObject?) ?: return null
        }
        @Suppress("UNCHECKED_CAST")
        return cur as D?
    }

    companion object {
        fun <R : KmfObject> startAt(rootType: KClass<R>) =
            KmfObjPath(rootType, rootType, emptyList())

        fun <R : KmfObject, D : KmfObject> build(
            rootType: KClass<R>,
            destType: KClass<D>,
            path: List<KmfAttribute.Unary>
        ): KmfObjPath<R, D> {
            var parent = rootType.kmfClass
            for (i in 0..path.lastIndex) {
                val attr = path[i]
                require(attr.kmfClass === parent) {
                    "Path at index $i: attribute $attr is not part of $parent."
                }
                require(attr.kind == KmfAttrKind.CHILD || attr.kind == KmfAttrKind.REFERENCE) {
                    "Path at index $i: attribute $attr must be either a child or reference attribute."
                }
                @Suppress("UNCHECKED_CAST")
                parent = (attr.valueType as KClass<out KmfObject>).kmfClass
            }

            require(parent === destType.kmfClass) {
                "destType $destType doesn't match end of path: $parent."
            }

            return KmfObjPath(rootType, destType, path)
        }
    }
}

operator fun <R : KmfObject, D : KmfObject, T : KmfObject> KmfObjPath<R, D>.div(
    property: KProperty1<D, T?>
): KmfObjPath<R, T> = append(property)