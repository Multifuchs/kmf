package de.mf.kmf.core

private val kmfPathIdValidator = """[\p{Alnum}_-]+""".toRegex()
private val kmfPathPart = """([^\[\]]+)(?:\[(\d+)\])?""".toRegex()

fun KmfObject.path(): String = buildString {
    val objPath = pathFromRoot()
    val lastWithId = objPath.indexOfLast { it.idOrNull() != null }
    check(lastWithId != -1) {
        "Can't build path for KmfObject: it or one of it descendants must have an id."
    }

    val lastId = objPath[lastWithId].idOrNull()!!
    check(kmfPathIdValidator.matches(lastId)) {
        "Can't build path for KmfObject: its id or the id of the last descendant which has one is not valid. It must match /${kmfPathIdValidator.pattern}/."
    }

    append("<id:").append(lastId).append(">")

    for (i in (lastWithId + 1)..objPath.lastIndex) {
        val pcAttr = objPath[i].parentChildAttribute!!
        append(".").append(pcAttr.name)
        if (pcAttr is KmfAttribute.List) {
            val parentChildList = pcAttr.get(objPath[i - 1])
            val indexInParentChildList = parentChildList.indexOf(objPath[i])
            check(indexInParentChildList != -1)
            append("[").append(indexInParentChildList).append("]")
        }
    }
}

class KmfResolver(
    private val rootObjects: Collection<KmfObject>
) {
    private val id2obj: Map<String, KmfObject> = buildMap {
        for (root in rootObjects) {
            root.iterateChildTree(includeThis = true) { obj ->
                val id = obj.idOrNull()
                if (id != null) {
                    require(put(id, obj) == null) {
                        "Duplicate id in rootObjects: $id."
                    }
                }
                true
            }
        }
    }

    fun resolve(path: String): KmfObject? {
        // Format: <id:foo>.childrenList.aChild
        val parts = path.split('.')
        require(parts.isNotEmpty()) { "Malformed path: path is empty." }
        val idPart = parts.first()
        require(idPart.startsWith("<id:") && idPart.endsWith(">")) {
            "Malformed path: first segment must contain id."
        }
        val rootId = idPart.removePrefix("<id:").removeSuffix(">")
        var cur = id2obj[rootId]
            ?: return null
        for (part in 1..parts.lastIndex) {
            val debugPath by lazy {
                parts.subList(0, part + 1).joinToString(".")
            }
            val match = kmfPathPart.matchEntire(parts[part])
            requireNotNull(match) {
                "Malformed part in path: $debugPath"
            }
            val propName = match.groupValues[1]
            val propIndex = match.groupValues[2]
            val attr = cur.kmfClass.allChildren.firstOrNull {
                it.name == propName
            }
            requireNotNull(attr) {
                "$debugPath not found: $propName is not a member of ${cur.kmfClass.kClass.qualifiedName}"
            }
            val next = when (attr) {
                is KmfAttribute.Unary -> {
                    require(propIndex.isEmpty()) { "Unexpected index in path: $debugPath" }
                    attr.get(cur) as KmfObject?
                }
                is KmfAttribute.List -> {
                    val propIntIndex = propIndex.toIntOrNull()
                    requireNotNull(propIntIndex) { "Missing index in path: $debugPath" }
                    val list = attr.get(cur)
                    val e = list.getOrNull(propIntIndex) as KmfObject?
                    e
                }
            } ?: return null

            cur = next
        }

        return cur
    }
}