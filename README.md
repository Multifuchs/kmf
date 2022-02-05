# KMF

KMF is framework and code generator for [Kotlin](https://kotlinlang.org/), 
which tries to be an easy to use alternative to
[EMF](https://github.com/eclipse/emf).

The EMF (Eclipse Modeling Framework) is a very powerful and bulletproof modeling
framework. The Framework itself is independent of the Eclipse IDE. 
The model classes are usually created using code generation. There are several
formats to describe your model:

- XML Files, which are usually hard to edit by hand, but there are good editors 
for that. Sadly, they are only available in Eclipse.
- XCore is a more "simple" Notation
- ...

There are also some ways to do generate classes without Eclipse, even using a
build system, such as Gradle or Maven. But it's quite hard to get them running.

On the other hand it's worth it to use EMF in almost any data-driven project.
One important feature of any *EObject* is that it can be observed. Therefore,
it is very easy to use it to bind values to UI-fields. Also, there are many
libraries, which are build on top of EMF. EMF-Edit for example provides you
tools to alter your model in a well-defined way with undo/redo-support out of
the box. Serialization is also no issue with EMF. There are XML and JSON
bindings available.

## KMF Objects

Every model class derives from another model class or `KmfObject`. It has
properties, but no custom methods.

A property is of one of three kinds:

- A simple value-property is a property for primitive values.
- A reference-property references another `KmfObject`, but doesn't contain it.
- A child-property also references another `KmfObject` and contains it.

A property has one of three multiplicities:
- nullable (0..1)
- not-null (1..1)
- list (0..n)

### Simple value properties

Simple value properties can have values with the following "primitive" types:
- String
- Int
- Long
- Double
- Boolean
- LocalDate
- LocalDateTime
- OffsetDateTime
- ZonedDateTime
- Any enum type

### Reference properties

A property which references other `KmfObject`. These references are not managed
like child reference properties are. Therefore, a references object doesn't know
that it is referenced by someone.

### Child reference properties

Every `KmfObject` can have multiple children, but only none or one parent. KMF
takes care, that this relationship stays consistent the whole time. If your
you assign a child to a parent, KMF checks if the child already has a parent. If
it does so, the child will be removed from the old parent.

### Adapters

Adapters is a concept, which is inspired by EMF. It allows you to extend an
`KmfObject` at runtime. Basically, an adapter is an object, which is attached to
one or more `KmfObjects` and receives notification whenever the state of an
object it is attached to changes.

One use might be to implement a change-recorder, which provides undo and redo
support or writes a history.

I use it a lot to attach validation results to an object. This is useful in a
UI, where form fields can react to them.

# KMF Class

For every model class exists one `KmfClass`, which provides a lot of reflective
information. This is very useful for libraries and utilities build on top of
KMF.

# Code generator

KMF provides a code generation plugin for gradle, which generates kotlin code
out of JSON. Here's an example from the test project:

```json
{
  "package": "de.mf.kmf.test",
  "imports": {
  },
  "classes": {
    "TestRoot": {
      "id": "String?",
      "simpleEnumProp": {
        "type": "de.mf.kmf.test.TestEnum",
        "default": "C"
      },
      "nullEnumProp": "de.mf.kmf.test.TestEnum?",
      "listEnumProp": "de.mf.kmf.test.TestEnum[]",
      "simpleStringProp": "String",
      "nullStringProp": "String?",
      "listStringProp": "String[]",
      "simpleDoubleProp": "Double",
      "nullDoubleProp": "Double?",
      "listDoubleProp": "Double[]",
      "simpleIntProp": "Int",
      "nullIntProp": "Int?",
      "listIntProp": "Int[]",
      "simpleLongProp": "Long",
      "nullLongProp": "Long?",
      "listLongProp": "Long[]",
      "simpleBooleanProp": "Boolean",
      "nullBooleanProp": "Boolean?",
      "listBooleanProp": "Boolean[]",
      "simpleLocalDateProp": "LocalDate",
      "nullLocalDateProp": "LocalDate?",
      "listLocalDateProp": "LocalDate[]",
      "simpleLocalDateTimeProp": "LocalDateTime",
      "nullLocalDateTimeProp": "LocalDateTime?",
      "listLocalDateTimeProp": "LocalDateTime[]",
      "simpleOffsetDateTimeProp": "OffsetDateTime",
      "nullOffsetDateTimeProp": "OffsetDateTime?",
      "listOffsetDateTimeProp": "OffsetDateTime[]",
      "simpleZonedDateTimeProp": "ZonedDateTime",
      "nullZonedDateTimeProp": "ZonedDateTime?",
      "listZonedDateTimeProp": "ZonedDateTime[]",
      "singleRef": "ref TestNodeClass?",
      "multiRef": "ref TestNodeClass[]",
      "child": "child TestNodeClass?",
      "children": "child TestNodeClass[]"
    },
    "TestExtendedRoot": {
      "superClass": "TestRoot",
      "isExtended": {
        "type": "Boolean",
        "default": "true"
      }
    },
    "TestNodeClass": {
      "name": "String",
      "children": "child TestNodeClass[]"
    }
  }
}
```
