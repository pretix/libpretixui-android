package eu.pretix.libpretixui.android.questions.form

class TelephoneNumberField(
    override val identifier: FormFieldIdentifier,
    override val label: String,
    override val default: String?,
    override val required: Boolean,
) : FormField {
    override fun validate(trimmedValue: String): String {
        // TODO: Validation currently happens on the PhoneEditText widget
        // Call libphonenumber here directly?
        // Until then: If the number is invalid, we will get an empty string

        if (trimmedValue.isBlank()) {
            throw ValidationException("Invalid telephone number supplied")
        }

        return trimmedValue
    }
}
