package eu.pretix.libpretixui.android.questions.form

@JvmInline
value class FormFieldIdentifier(val value: String)

sealed interface FormField {
    val identifier: FormFieldIdentifier
    val label: String
    val default: String?
    val required: Boolean

    open fun trim(value: String?): String {
        return if ((value == null || value.trim { it <= ' ' } == "")) {
            ""
        } else {
            value
        }
    }

    open fun checkRequired(trimmedValue: String) {
        if (required && trimmedValue.isBlank()) {
            throw ValidationException("Question is required")
        }
    }

    abstract fun validate(trimmedValue: String): String
}

class ValidationException(msg: String) : Exception(msg)
