package de.mf.kmf.test.b

import de.mf.kmf.api.KMF
import de.mf.kmf.test.TestEntity
import org.eclipse.emf.ecore.EObject

@KMF
interface TestB : EObject {
    var xxx: String

    var aaa: TestEntity?
}