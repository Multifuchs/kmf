package de.mf.kmf.api

import org.eclipse.emf.common.notify.Notification
import org.eclipse.emf.common.notify.NotificationChain
import org.eclipse.emf.common.util.EList
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.InternalEObject
import org.eclipse.emf.ecore.impl.ENotificationImpl
import org.eclipse.emf.ecore.impl.MinimalEObjectImpl
import org.eclipse.emf.ecore.util.EDataTypeEList
import org.eclipse.emf.ecore.util.EObjectContainmentEList
import org.eclipse.emf.ecore.util.EObjectResolvingEList
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.jvmErasure

open class KMFEObjectImpl : MinimalEObjectImpl.Container() {

    companion object {
        fun <T : EObject> containmentList(featureId: Int) =
            ContainmentListLoader<T>(featureId)

        fun <T : EObject> referenceList(featureId: Int) =
            ReferenceListLoader<T>(featureId)

        fun <T> dataList(featureId: Int) =
            DataListLoader<T>(featureId)

        fun <T> data(featureId: Int, defaultValue: T) =
            DataDelegate(featureId, defaultValue)

        fun <T : EObject> reference(featureId: Int) =
            ReferenceDelegate<T>(featureId)
    }

    class ContainmentListLoader<T : EObject>(private val featureId: Int) {
        operator fun provideDelegate(
            thisRef: KMFEObjectImpl,
            prop: KProperty<*>
        ): EListDelegate<T> {
            val x = prop.returnType.arguments[0].type?.jvmErasure?.java!!
            return EListDelegate<T>(
                EObjectContainmentEList<T>(
                    x,
                    thisRef,
                    featureId
                )
            )
        }
    }

    class ReferenceListLoader<T : EObject>(private val featureId: Int) {
        operator fun provideDelegate(
            thisRef: KMFEObjectImpl,
            prop: KProperty<*>
        ): EListDelegate<T> {
            return EListDelegate(
                EObjectResolvingEList(
                    prop.returnType.arguments[0].type?.jvmErasure?.java!!,
                    thisRef,
                    featureId
                )
            )
        }
    }

    class DataListLoader<T>(private val featureId: Int) {
        operator fun provideDelegate(
            thisRef: KMFEObjectImpl,
            prop: KProperty<*>
        ): EListDelegate<T> {
            return EListDelegate(
                EDataTypeEList(
                    prop.returnType.arguments[0].type?.jvmErasure?.java!!,
                    thisRef,
                    featureId
                )
            )
        }
    }

    class EListDelegate<T>(val eList: EList<T>) :
        ReadOnlyProperty<KMFEObjectImpl, EList<T>> {
        override fun getValue(
            thisRef: KMFEObjectImpl,
            property: KProperty<*>
        ): EList<T> {
            return eList
        }
    }

    class DataDelegate<T>(val featureId: Int, defaultValue: T) :
        ReadWriteProperty<KMFEObjectImpl, T> {

        private var curValue: T = defaultValue

        override fun getValue(
            thisRef: KMFEObjectImpl,
            property: KProperty<*>
        ): T {
            return curValue
        }

        override fun setValue(
            thisRef: KMFEObjectImpl,
            property: KProperty<*>,
            value: T
        ) {
            val oldValue = curValue
            curValue = value
            if (thisRef.eNotificationRequired()) {
                thisRef.eNotify(
                    ENotificationImpl(
                        thisRef,
                        Notification.SET,
                        featureId,
                        oldValue,
                        value
                    )
                )
            }
        }

    }

    class ReferenceDelegate<T : EObject>(val featureId: Int) :
        ReadWriteProperty<KMFEObjectImpl, T?> {

        private var curValue: T? = null

        override fun getValue(
            thisRef: KMFEObjectImpl,
            property: KProperty<*>
        ): T? {
            val cur = curValue
            if (cur?.eIsProxy() == true) {
                val old = cur as InternalEObject
                curValue = thisRef.eResolveProxy(old) as T?
                if (curValue !== old && thisRef.eNotificationRequired()) {
                    thisRef.eNotify(
                        ENotificationImpl(
                            thisRef,
                            Notification.RESOLVE,
                            featureId,
                            old,
                            curValue
                        )
                    )
                }
            }
            return curValue
        }

        override fun setValue(
            thisRef: KMFEObjectImpl,
            property: KProperty<*>,
            value: T?
        ) {
            if (value != curValue) {
                var msgs: NotificationChain? = null
                if (curValue != null) {
                    msgs = (curValue as InternalEObject).eInverseRemove(
                        thisRef,
                        InternalEObject.EOPPOSITE_FEATURE_BASE - featureId,
                        null,
                        msgs
                    )
                }
                if (value != null) {
                    msgs = (value as InternalEObject).eInverseAdd(
                        thisRef,
                        InternalEObject.EOPPOSITE_FEATURE_BASE - featureId,
                        null,
                        msgs
                    )
                }

                val oldValue = curValue
                curValue = value
                if (thisRef.eNotificationRequired()) {
                    val notification = ENotificationImpl(
                        thisRef,
                        Notification.SET,
                        featureId,
                        oldValue,
                        value
                    )
                    if (msgs == null) msgs = notification
                    else msgs.add(notification)
                }

                msgs?.dispatch()
            } else if (thisRef.eNotificationRequired()) {
                thisRef.eNotify(
                    ENotificationImpl(
                        thisRef,
                        Notification.SET,
                        featureId,
                        value,
                        value
                    )
                )
            }
        }
    }

    class DummyDelegate<T>() : ReadWriteProperty<KMFEObjectImpl, T> {
        override fun getValue(
            thisRef: KMFEObjectImpl,
            property: KProperty<*>
        ): T {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun setValue(
            thisRef: KMFEObjectImpl,
            property: KProperty<*>,
            value: T
        ) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

    }

}