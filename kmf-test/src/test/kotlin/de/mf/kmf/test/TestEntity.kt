package de.mf.kmf.test

import de.mf.kmf.api.Contains
import de.mf.kmf.api.KMF
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.EObject

@KMF
interface TestEntity : EObject {

    var isDope: Boolean

    @Contains
    var other: TestEntity?

    @Contains
    val otherList: EList<TestEntity>

    var xO: String
}
