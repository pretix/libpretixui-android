package eu.pretix.libpretixui.android.questions.form

import eu.pretix.libpretixsync.db.QuestionOption

data class MultipleChoiceField(
    override val identifier: FormFieldIdentifier,
    override val label: String,
    override val default: String?,
    override val required: Boolean,
    val options: List<QuestionOption>,
) : FormField {
    override fun validate(trimmedValue: String): String {
        return trimmedValue
    }
}
