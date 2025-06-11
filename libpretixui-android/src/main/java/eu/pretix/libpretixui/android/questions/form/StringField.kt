package eu.pretix.libpretixui.android.questions.form

class StringField(
    override val identifier: FormFieldIdentifier,
    override val label: String,
    override val default: String?,
    override val required: Boolean,
) : FormField {
    override fun validate(trimmedValue: String): String {
        return trimmedValue
    }
}
