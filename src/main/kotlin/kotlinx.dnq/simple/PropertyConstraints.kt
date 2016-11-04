package kotlinx.dnq.simple

import com.jetbrains.teamsys.dnq.database.PropertyConstraint
import jetbrains.teamsys.dnq.runtime.constraints.inRange
import jetbrains.teamsys.dnq.runtime.constraints.regexp
import java.net.MalformedURLException
import java.net.URI


class PropertyConstraintBuilder<T>() {
    val constraints = mutableListOf<PropertyConstraint<T>>()
}

fun PropertyConstraintBuilder<String?>.regex(pattern: Regex, message: String? = null) {
    constraints.add(regexp().apply {
        this.pattern = pattern.toPattern()
        if (message != null) {
            this.message = message
        }
    })
}

fun PropertyConstraintBuilder<String?>.email(pattern: Regex? = null, message: String? = null) {
    constraints.add(jetbrains.teamsys.dnq.runtime.constraints.email().apply {
        if (pattern != null) {
            this.pattern = pattern.toPattern()
        }
        if (message != null) {
            this.message = message
        }
    })
}

fun PropertyConstraintBuilder<String?>.containsNone(chars: String, message: String? = null) {
    constraints.add(jetbrains.teamsys.dnq.runtime.constraints.containsNone().apply {
        this.chars = chars
        if (message != null) {
            this.message = message
        }
    })
}

fun PropertyConstraintBuilder<String?>.alpha(message: String? = null) {
    constraints.add(jetbrains.teamsys.dnq.runtime.constraints.alpha().apply {
        if (message != null) {
            this.message = message
        }
    })
}

fun PropertyConstraintBuilder<String?>.numeric(message: String? = null) {
    constraints.add(jetbrains.teamsys.dnq.runtime.constraints.numeric().apply {
        if (message != null) {
            this.message = message
        }
    })
}

fun PropertyConstraintBuilder<String?>.alphaNumeric(message: String? = null) {
    constraints.add(jetbrains.teamsys.dnq.runtime.constraints.alphaNumeric().apply {
        if (message != null) {
            this.message = message
        }
    })
}

fun PropertyConstraintBuilder<String?>.url(message: String? = null) {
    constraints.add(jetbrains.teamsys.dnq.runtime.constraints.url().apply {
        if (message != null) {
            this.message = message
        }
    })
}

fun PropertyConstraintBuilder<String?>.length(min: Int = 0, max: Int = Int.MAX_VALUE, message: String? = null) {
    constraints.add(jetbrains.teamsys.dnq.runtime.constraints.length().apply {
        if (min > 0) {
            this.min = min
        }
        if (max < Int.MAX_VALUE) {
            this.max = max
        }
        if (message != null) {
            when {
                min > 0 && max < Int.MAX_VALUE -> this.rangeMessage = message
                min > 0 -> this.minMessage = message
                max < Int.MAX_VALUE -> this.maxMessage = message
            }
        }
    })
}

fun PropertyConstraintBuilder<String?>.uri(message: String? = null) {
    constraints.add(object : PropertyConstraint<String?>() {
        var message = message ?: "is not a valid URI"

        override fun isValid(propertyValue: String?): Boolean {
            return if (propertyValue != null) {
                try {
                    URI(propertyValue)
                    true
                } catch (e: MalformedURLException) {
                    false
                }
            } else true
        }

        override fun getExceptionMessage(propertyName: String, propertyValue: String?) =
                "$propertyName should be valid URI but was $propertyValue"

        override fun getDisplayMessage(propertyName: String, propertyValue: String?) =
                this.message
    })
}

class RequireIfConstraint<T>(val message: String?, val predicate: () -> Boolean) : PropertyConstraint<T>() {
    override fun isValid(value: T): Boolean {
        return !predicate() || value != null
    }

    override fun getExceptionMessage(propertyName: String?, propertyValue: T): String {
        return "Value for $propertyName is required"
    }

    override fun getDisplayMessage(propertyName: String?, propertyValue: T) = message ?: "required"
}

fun <T> PropertyConstraintBuilder<T>.requireIf(message: String? = null, predicate: () -> Boolean) {
    constraints.add(RequireIfConstraint<T>(message, predicate))
}

open class InRange<T : Number?>() : inRange() {
    val typed = object : PropertyConstraint<T>() {
        override fun getExceptionMessage(propertyName: String?, propertyValue: T): String {
            return this@InRange.getExceptionMessage(propertyName, propertyValue)
        }

        override fun isValid(value: T): Boolean {
            return this@InRange.isValid(value)
        }
    }
}

fun <T : Number?> PropertyConstraintBuilder<T>.min(min: Long, message: String? = null) {
    constraints.add(InRange<T>().apply {
        this.min = min
        if (message != null) {
            this.minMessage = message
        }
    }.typed)
}

fun <T : Number?> PropertyConstraintBuilder<T>.max(max: Long, message: String? = null) {
    constraints.add(InRange<T>().apply {
        this.max = max
        if (message != null) {
            this.maxMessage = message
        }
    }.typed)
}

fun PropertyConstraintBuilder<Long?>.past(message: String? = null) {
    constraints.add(jetbrains.teamsys.dnq.runtime.constraints.past().apply {
        if (message != null) {
            this.message = message
        }
    })
}

fun PropertyConstraintBuilder<Long?>.future(message: String? = null) {
    constraints.add(jetbrains.teamsys.dnq.runtime.constraints.future().apply {
        if (message != null) {
            this.message = message
        }
    })
}
