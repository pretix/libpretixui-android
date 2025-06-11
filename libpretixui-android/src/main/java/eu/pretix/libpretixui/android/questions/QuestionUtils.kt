package eu.pretix.libpretixui.android.questions

import eu.pretix.libpretixsync.check.QuestionType
import eu.pretix.libpretixsync.models.Question
import eu.pretix.libpretixui.android.questions.form.BooleanField
import eu.pretix.libpretixui.android.questions.form.FormField
import eu.pretix.libpretixui.android.questions.form.FormFieldIdentifier
import eu.pretix.libpretixui.android.questions.form.MultiLineStringField
import eu.pretix.libpretixui.android.questions.form.NumberField
import eu.pretix.libpretixui.android.questions.form.StringField
import eu.pretix.libpretixui.android.questions.form.TelephoneNumberField

fun Question.toFormField(): FormField {
    return when (this.type) {
        QuestionType.N -> NumberField(
            identifier = FormFieldIdentifier(identifier),
            label = question,
            default = default,
            required = required,
        )

        QuestionType.S -> StringField(
            identifier = FormFieldIdentifier(identifier),
            label = question,
            default = default,
            required = required,
        )

        QuestionType.T -> MultiLineStringField(
            identifier = FormFieldIdentifier(identifier),
            label = question,
            default = default,
            required = required,
        )

        QuestionType.B -> BooleanField(
            identifier = FormFieldIdentifier(identifier),
            label = question,
            default = default,
            required = required,
        )

        QuestionType.TEL -> TelephoneNumberField(
            identifier = FormFieldIdentifier(identifier),
            label = question,
            default = default,
            required = required,
        )

        else -> TODO()
    }
}
