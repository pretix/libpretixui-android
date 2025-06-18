package eu.pretix.libpretixui.android.questions.form

data class DialogOutput(
    val fieldIdentifier: FormFieldIdentifier,
    val value: String,

    /**
     * Indicates whether the value contains server IDs of selected options
     * E.g. used when storing question answers in the DB on ReceiptLines
     */
    val hasOptions: Boolean = false
)
