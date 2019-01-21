/**
 * Copyright 2006 - 2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kotlinx.dnq.link

import jetbrains.exodus.query.metadata.AssociationEndCardinality
import jetbrains.exodus.query.metadata.AssociationEndType
import kotlinx.dnq.RequiredPropertyUndefinedException
import kotlinx.dnq.XdEntity
import kotlinx.dnq.XdEntityType
import kotlinx.dnq.query.XdMutableQuery
import kotlinx.dnq.util.reattach
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

class XdManyChildrenToParentLink<R : XdEntity, T : XdEntity>(
        oppositeEntityType: XdEntityType<T>,
        override val oppositeField: KProperty1<T, XdMutableQuery<R>>,
        dbPropertyName: String?
) : ScalarRequiredLink<R, T>, XdLink<R, T>(
        oppositeEntityType,
        dbPropertyName,
        null,
        AssociationEndCardinality._1,
        AssociationEndType.ChildEnd,
        onDelete = OnDeletePolicy.CLEAR,
        onTargetDelete = OnDeletePolicy.CASCADE
) {

    override fun getValue(thisRef: R, property: KProperty<*>): T {
        val parent = thisRef.reattach().getLink(property.name)
                ?: throw RequiredPropertyUndefinedException(thisRef, property)

        return oppositeEntityType.wrap(parent)
    }

    override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        value.reattach().addChild(oppositeField.name, property.name, thisRef.reattach())
    }

    override fun isDefined(thisRef: R, property: KProperty<*>): Boolean {
        return thisRef.reattach().getLink(property.name) != null
    }
}

