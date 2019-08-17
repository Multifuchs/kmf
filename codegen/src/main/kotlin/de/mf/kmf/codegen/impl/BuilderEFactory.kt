package de.mf.kmf.codegen.impl

import com.squareup.kotlinpoet.*
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.impl.EFactoryImpl

fun buildEFactory(mp: ModelPackage): FileSpec {
    require(!mp.isForeign)
    val json = mp.codeGen.jsons.first {
        it.requireString("nsURI") == mp.nsURI
    }
    val factoryType = mp.poetEFactoryType
    return FileSpec.builder(factoryType.packageName, factoryType.simpleName)
        .addType(TypeSpec.objectBuilder(factoryType.simpleName)
            .superclass(EFactoryImpl::class)
            .addFunction(FunSpec.builder("create")
                .addModifiers(KModifier.OVERRIDE)
                .returns(EObject::class)
                .addParameter("eClass", EClass::class)
                .addCode(buildCodeBlock {
                    beginControlFlow("return when (eClass.classifierID)")
                    for (mt in mp.allModelTypes) {
                        addStatement("%T.${mt.poetEClassID.simpleName} -> %T()", mt.poetEClassID.enclosingClassName, mt.poetEObjectTypeName)
                    }
                    addStatement("else -> throw %T(%P)", IllegalArgumentException::class,
                        "The class '\${eClass.name}' is not a valid classifier")
                    endControlFlow()
                })
                .build()
            )
            .build()
        )
        .build()
}