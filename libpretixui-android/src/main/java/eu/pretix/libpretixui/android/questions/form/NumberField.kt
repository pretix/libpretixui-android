package eu.pretix.libpretixui.android.questions.form

import java.math.BigDecimal

class NumberField(
    override val identifier: FormFieldIdentifier,
    override val label: String,
    override val default: String?,
    override val required: Boolean,
) : FormField {
    override fun validate(trimmedValue: String): String {
        try {
            return BigDecimal(trimmedValue).toPlainString()
        } catch (e: NumberFormatException) {
            throw ValidationException("Invalid number supplied")
        }
    }
}
