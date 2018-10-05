# KMF

KMF is a [Kotlin](https://kotlinlang.org/) integration of [EMF](https://github.com/eclipse/emf).

EMF (Eclipse Modeling Framework) is a very powerfull and battleproof modeling framework. The Framework itself is independent of the Eclipse IDE. The Modelclasses are usally created using code generation. There are several formats to descripe your model, which generators exist for:

- XML Files, which are usally hard to edit, but there are good editors for that. Sadly they are only available in Eclipse.
- XCore is a more "simple" Notation
- ...

There are also some ways to do generate classes without Eclipse, even using a build system, such as Gradle or Maven. But it's quite hard to get them running.

On the other hand it's worth it to use EMF in almost any data-driven project. One important feature of any *EObject* is that it can be observed. Therefore, it is very easy to use it to bind values to UI-fields. Also, there are many libraries, which are build on top of EMF. EMF-Edit for example provides you tools to alter your model in a well defined way with undo/redo-support out of the box. Serialization is also no issue with EMF. There are XML and JSON bindings available.

## Model

KMF allows you to create you model using kotlin. All you have to do is create Interfaces, let them extend `EObject` and anotate them with `@KMF`.

```kotlin
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
```

## Code Generation

KMF uses [kapt](https://kotlinlang.org/docs/reference/kapt.html) to generate the `EPackage`, `EFactory` classes and the implementations for your interfaces as well as some utility functions. Using Gradle, this is very easy to setup:

```groovy
// Example taken from the kmf-test module.
apply plugin: "kotlin-kapt"

dependencies {
    testCompile project(":kmf-api")
    // this is the code generator - it will find all interfaces with @KMF automatically
    kaptTest project(":kmf-kapt")
}

kapt {
    javacOptions {
        option("-source", '1.8')
        option("-target", '1.8')
    }
}
```

## Limitations

Since this is a very young project, the code generator doesn't support all features EMF and Ecore provide.
- Inheritance is not supported
- Generics aren't supported
- Enums aren't supported