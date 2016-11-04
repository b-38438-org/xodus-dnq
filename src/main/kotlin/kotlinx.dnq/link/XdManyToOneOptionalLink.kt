package kotlinx.dnq.link

import com.jetbrains.teamsys.dnq.association.AssociationSemantics
import com.jetbrains.teamsys.dnq.association.UndirectedAssociationSemantics
import jetbrains.exodus.entitystore.metadata.AssociationEndCardinality
import jetbrains.exodus.entitystore.metadata.AssociationEndType
import kotlinx.dnq.XdEntity
import kotlinx.dnq.XdEntityType
import kotlinx.dnq.query.XdMutableQuery
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

class XdManyToOneOptionalLink<R : XdEntity, T : XdEntity>(
        val entityType: XdEntityType<T>,
        override val oppositeField: KProperty1<T, XdMutableQuery<R>>,
        onDeletePolicy: OnDeletePolicy,
        onTargetDeletePolicy: OnDeletePolicy) :
        ReadWriteProperty<R, T?>,
        XdLink<R, T>(entityType, null,
                AssociationEndCardinality._0_1, AssociationEndType.UndirectedAssociationEnd, onDeletePolicy, onTargetDeletePolicy) {

    override fun getValue(thisRef: R, property: KProperty<*>): T? {
        return AssociationSemantics.getToOne(thisRef.entity, property.name)?.let { value ->
            entityType.wrap(value)
        }
    }

    override fun setValue(thisRef: R, property: KProperty<*>, value: T?) {
        if (value != null) {
            UndirectedAssociationSemantics.setManyToOne(thisRef.entity, property.name, oppositeField.name, value.entity)
        } else {
            val currentValue = getValue(thisRef, property)
            if (currentValue != null) {
                UndirectedAssociationSemantics.removeOneToMany(currentValue.entity, oppositeField.name, property.name, thisRef.entity)
            }
        }
    }

    override fun isDefined(thisRef: R, property: KProperty<*>) = getValue(thisRef, property) != null
}

