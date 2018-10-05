package de.mf.kmf.test

import de.mf.kmf.api.Contains
import de.mf.kmf.api.KMF
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.EObject

@KMF
interface Person : EObject {
    var name: String?
}

@KMF
interface Car : EObject {
    var color: String?

    // non-containment reference
    var owner: EObject?

    // containment reference
    @Contains
    val wheels: EList<Wheel>
}

@KMF
interface Wheel : EObject {
    var diameter: Double
}

fun main(args: Array<String>) {
    val me = Person::class.create().apply {
        name = "Friedrich"
    }
    val myCar = Car::class.create().apply {
        color = "Blue"
        owner = me
        repeat(4) {
            wheels += Wheel::class.create().apply { diameter = 42.0 }
        }
    }
    println(me)
    println(myCar)
}