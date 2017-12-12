/**
 * Copyright 2006 - 2017 JetBrains s.r.o.
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
package kotlinx.dnq.java.time

import jetbrains.exodus.util.LightOutputStream
import kotlinx.dnq.RequiredPropertyUndefinedException
import kotlinx.dnq.XdEntity
import kotlinx.dnq.simple.*
import kotlinx.dnq.simple.custom.type.XdComparableBinding
import kotlinx.dnq.util.XdPropertyCachedProvider
import java.io.ByteArrayInputStream
import java.time.OffsetTime

object OffsetTimeBinding : XdComparableBinding<OffsetTime>() {

    override fun write(stream: LightOutputStream, value: OffsetTime) {
        LocalTimeBinding.write(stream, value.toLocalTime())
        ZoneOffsetBinding.write(stream, value.offset)
    }

    override fun read(stream: ByteArrayInputStream): OffsetTime {
        return OffsetTime.of(LocalTimeBinding.read(stream), ZoneOffsetBinding.read(stream))
    }
}

fun <R : XdEntity> xdOffsetTimeProp(dbName: String? = null, constraints: Constraints<R, OffsetTime?>? = null) =
        XdPropertyCachedProvider {
            XdNullableProperty<R, OffsetTime>(
                    OffsetTime::class.java,
                    dbName,
                    constraints.collect()
            )
        }

fun <R : XdEntity> xdRequiredOffsetTimeProp(unique: Boolean = false, dbName: String? = null, constraints: Constraints<R, OffsetTime?>? = null) =
        XdPropertyCachedProvider {
            XdProperty<R, OffsetTime>(
                    OffsetTime::class.java,
                    dbName,
                    constraints.collect(),
                    if (unique) XdPropertyRequirement.UNIQUE else XdPropertyRequirement.REQUIRED,
                    { e, p -> throw RequiredPropertyUndefinedException(e, p) }
            )
        }