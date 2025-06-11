package eu.pretix.libpretixui.android.questions.form

data class DialogInput(
    val field: FormField,
    val value: String?,
    val previousValue: String?,

    //TODO: dependencies
    val shouldShow: (currentValues: Map<FormFieldIdentifier, String>) -> Boolean = { true },

    /**
     * Checks if a warning should be displayed for the provided value
     *
     * Should return the error text if a warning should be displayed, null otherwise.
     */
    val checkForWarning: (trimmedValue: String) -> String? = { null }
)
