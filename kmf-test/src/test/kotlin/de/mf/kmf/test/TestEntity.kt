package de.mf.kmf.test

import de.mf.kmf.api.Contains
import de.mf.kmf.api.KMF
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.EObject

@KMF
interface TestEntity : EObject {
    @get:Contains
    var other: TestEntity?

    @get:Contains
    val otherList: EList<TestEntity>
}
