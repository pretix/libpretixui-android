package eu.pretix.libpretixui.android.questions.form

import eu.pretix.libpretixsync.db.QuestionLike

class BooleanField(
    override val identifier: FormFieldIdentifier,
    override val label: String,
    override val default: String?,
    override val required: Boolean,
) : FormField {
    override fun validate(trimmedValue: String): String {
        // TODO: Fail if not True or False
        return if (trimmedValue == "True" || trimmedValue == "true") "True" else "False"
    }

    override fun trim(value: String?): String {
        return if (value == "True" || value == "true") "True" else "False"
    }

    override fun checkRequired(trimmedValue: String) {
        if (required && trimmedValue != "True") {
            throw QuestionLike.ValidationException("Question is required")
        }
    }
}
