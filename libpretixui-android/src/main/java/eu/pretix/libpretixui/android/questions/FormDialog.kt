package eu.pretix.libpretixui.android.questions

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.widget.doAfterTextChanged
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.github.ialokim.phonefield.PhoneEditText
import eu.pretix.libpretixui.android.PhotoCaptureActivity
import eu.pretix.libpretixui.android.R
import eu.pretix.libpretixui.android.questions.QuestionsDialog.QuestionInvalid
import eu.pretix.libpretixui.android.questions.form.BooleanField
import eu.pretix.libpretixui.android.questions.form.DialogInput
import eu.pretix.libpretixui.android.questions.form.DialogOutput
import eu.pretix.libpretixui.android.questions.form.FormField
import eu.pretix.libpretixui.android.questions.form.FormFieldIdentifier
import eu.pretix.libpretixui.android.questions.form.MultiLineStringField
import eu.pretix.libpretixui.android.questions.form.MultipleChoiceField
import eu.pretix.libpretixui.android.questions.form.NumberField
import eu.pretix.libpretixui.android.questions.form.StringField
import eu.pretix.libpretixui.android.questions.form.TelephoneNumberField
import eu.pretix.libpretixui.android.questions.form.ValidationException
import java.io.File
import java.lang.IllegalStateException
import java.text.SimpleDateFormat
import java.util.HashMap
import java.util.Locale

// TODO: Why the interface?
interface FormDialogInterface : DialogInterface {
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean

    fun setOnCancelListener(listener: DialogInterface.OnCancelListener?)

    fun isShowing(): Boolean

    fun onRestoreInstanceState(savedInstanceState: Bundle)

    fun onSaveInstanceState(): Bundle
}

sealed interface FormDialogHeader {
    data class LineItemHeader(
        val attendeeName: String? = null,
        val attendeeDOB: String? = null,
        val ticketId: String? = null,
        val ticketType: String? = null,
    ) : FormDialogHeader

    data object OrderHeader : FormDialogHeader
}

class FormDialog(
    header: FormDialogHeader,
    val ctx: Activity,
    val inputs: List<DialogInput>,
    val defaultCountry: String?,
    val glideLoader: ((String) -> GlideUrl)? = null,
    val onComplete: ((List<DialogOutput>) -> Unit),
    val clonePictures: Boolean = false,
    val allAnswersAreOptional: Boolean = false,
) : AlertDialog(ctx), FormDialogInterface {
    companion object {
        // TODO: Use DateTimeFormatter
        val hf = SimpleDateFormat("HH:mm", Locale.US)
        val wf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val wfServer = SimpleDateFormat("yyyy-MM-dd HH:mm:ssX", Locale.US)
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private val fieldViews = HashMap<FormFieldIdentifier, Any>()
    private val labels = HashMap<FormFieldIdentifier, TextView>()
    private val warnings = HashMap<FormFieldIdentifier, TextView>()
    private val setters = HashMap<FormFieldIdentifier, ((String?) -> Unit)>()
    private var currentValues = mutableMapOf<FormFieldIdentifier, DialogOutput>()

    private var v: View = LayoutInflater.from(context).inflate(R.layout.dialog_questions, null)
    private var waitingForAnswerFor: FormFieldIdentifier? = null

    init {
        setView(v)

        when (header) {
            is FormDialogHeader.LineItemHeader -> {
                v.findViewById<View>(R.id.clOrderInfo).visibility = View.GONE
                v.findViewById<View>(R.id.clAttendeeInfo).visibility = View.VISIBLE

                val attendeeName = header.attendeeName
                val attendeeDOB = header.attendeeDOB
                val ticketType = header.ticketType
                val ticketId = header.ticketId

                if (attendeeName.isNullOrBlank()) {
                    v.findViewById<TextView>(R.id.tvAttendeeName).visibility = View.GONE
                } else {
                    v.findViewById<TextView>(R.id.tvAttendeeName).text = attendeeName
                }
                if (attendeeDOB.isNullOrBlank()) {
                    v.findViewById<TextView>(R.id.tvAttendeeDOB).visibility = View.GONE
                } else {
                    v.findViewById<TextView>(R.id.tvAttendeeDOB).text = attendeeDOB
                }
                if (ticketType.isNullOrBlank()) {
                    v.findViewById<TextView>(R.id.tvTicketType).visibility = View.GONE
                } else {
                    v.findViewById<TextView>(R.id.tvTicketType).text = ticketType
                }
                if (ticketId.isNullOrBlank()) {
                    v.findViewById<TextView>(R.id.tvTicketId).visibility = View.GONE
                } else {
                    v.findViewById<TextView>(R.id.tvTicketId).text = ticketId
                }
                if (ticketId.isNullOrBlank() && ticketType.isNullOrBlank() && attendeeName.isNullOrBlank()) {
                    v.findViewById<View>(R.id.clAttendeeInfo).visibility = View.GONE
                }
            }

            FormDialogHeader.OrderHeader -> {
                v.findViewById<View>(R.id.clAttendeeInfo).visibility = View.GONE
                v.findViewById<View>(R.id.clOrderInfo).visibility = View.VISIBLE
            }
        }

        setButton(DialogInterface.BUTTON_POSITIVE, ctx.getString(R.string.cont), null as DialogInterface.OnClickListener?)
        setButton(DialogInterface.BUTTON_NEUTRAL, ctx.getString(R.string.cancel)) { p0, p1 ->
            cancel()
        }

        if (inputs.any { it.previousValue != null }) {
            setButton(DialogInterface.BUTTON_NEUTRAL, ctx.getString(R.string.copy), null as DialogInterface.OnClickListener?)
        }

        setOnShowListener {
            getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                submit()
            }
            if (inputs.any { it.previousValue != null }) {
                getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    for (input in inputs) {
                        if (setters.containsKey(input.field.identifier)) {
                            setters[input.field.identifier]!!(input.previousValue)
                        }
                    }
                    if (fieldViews[inputs.first().field.identifier] is EditText && fieldViews[inputs.first().field.identifier] !is DatePickerField) {
                        (fieldViews[inputs.first().field.identifier] as EditText).selectAll()
                        (fieldViews[inputs.first().field.identifier] as EditText).requestFocus()
                    }
                    checkForWarnings()
                }
            }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.isCtrlPressed) {
                    submit()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }

        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        addFields()
    }

    private fun submit() {
        var has_errors = false

        // Step 1: Extract values from views
        inputs.forEach { input ->
            val output = extractValues(input.field)
            currentValues[input.field.identifier] = output
        }

        // Step 2: Validate
        for (input in inputs) {
            // Check if conditional visibility changed (e.g. Question dependencies)
            // This needs to happen before validation, as we don't want to validate fields that
            // are not shown (they might be required)
            if (!input.shouldShow(currentValues)) {
                continue
            }

            val field = input.field
            val identifier = field.identifier
            val view = fieldViews[identifier] ?: throw IllegalStateException("Could not get view for identifier $identifier")
            val output = currentValues[identifier] ?: throw IllegalStateException("Could not get trimmed value for identifier $identifier")
            try {
                validate(field, output.value, allAnswersAreOptional)
                addQuestionsError(ctx, view, labels[field.identifier], 0)
            } catch (e: QuestionInvalid) {
                addQuestionsError(ctx, view, labels[field.identifier], e.msgid)
                has_errors = true
            }
        }

        if (!has_errors) {
            dismiss()

            onComplete(currentValues.values.toList())
        } else {
            Toast.makeText(ctx, R.string.question_validation_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractValues(field: FormField): DialogOutput {
        val view = fieldViews[field.identifier]
        val output = when (field) {
//            QuestionType.S, QuestionType.T, QuestionType.EMAIL -> {
//                answer = (field as EditText).text.toString()
//                empty = answer.trim() == ""
//            }
            is StringField,
            is MultiLineStringField -> {
                DialogOutput(
                    fieldIdentifier = field.identifier,
                    value = field.trim((view as EditText).text.toString()),
                )
            }
            is TelephoneNumberField -> {
                val widget = (view as PhoneEditText)

                val value = if (!widget.isValid) {
                    ""
                } else {
                    widget.phoneNumberE164 ?: ""
                }

                DialogOutput(
                    fieldIdentifier = field.identifier,
                    value = field.trim(value),
                )
            }
            is NumberField -> {
                DialogOutput(
                    fieldIdentifier = field.identifier,
                    value = field.trim((view as EditText).text.toString()),
                )
            }
            is BooleanField -> {
                val value = if ((view as CheckBox).isChecked) "True" else "False"
                DialogOutput(
                    fieldIdentifier = field.identifier,
                    value = field.trim(value),
                )
            }
//            QuestionType.F -> {
//                val fieldset = field as List<View>
//                answer = if (fieldset[0].tag is String && (fieldset[0].tag as String).contains("://"))
//                    fieldset[0].tag as String
//                else if (fieldset[0].tag != null)
//                    "file://${fieldset[0].tag as String}"
//                else
//                    ""
//                empty = answer.trim() == ""
//            }
//            QuestionType.M -> {
//                empty = true
//                val aw = StringBuilder()
//                for (f in (field as List<CheckBox>)) {
//                    if (f.isChecked) {
//                        if (!empty) {
//                            aw.append(",")
//                        }
//                        aw.append((f.tag as QuestionOption).server_id)
//                        options.add(f.tag as QuestionOption)
//                        empty = false
//                    }
//                }
//                answer = aw.toString()
//            }
            is MultipleChoiceField -> {
                var empty = true
                val aw = StringBuilder()
                for (f in (field as List<CheckBox>)) {
                    if (f.isChecked) {
                        if (!empty) {
                            aw.append(",")
                        }
                        aw.append(f.tag as Long)
                        empty = false
                    }
                }

                DialogOutput(
                    fieldIdentifier = field.identifier,
                    value = field.trim(aw.toString()),
                    hasOptions = true,
                )
            }
//            QuestionType.CC -> {
//                val opt = ((field as Spinner).selectedItem as CountryCode)
//                answer = opt.alpha2
//            }
//            QuestionType.C -> {
//                val opt = ((field as Spinner).selectedItem as QuestionOption)
//                if (opt.server_id == 0L) {
//                    empty = true
//                } else {
//                    answer = opt.server_id.toString()
//                    options.add(opt)
//                }
//            }
//            QuestionType.D -> {
//                empty = ((field as DatePickerField).value == null)
//                if (!empty) {
//                    answer = QuestionsDialog.df.format(field.value!!.time)
//                }
//            }
//            QuestionType.H -> {
//                empty = ((field as TimePickerField).value == null)
//                if (!empty) {
//                    answer = QuestionsDialog.hf.format(field.value!!.toDateTimeToday().toDate())
//                }
//            }
//            QuestionType.W -> {
//                val fieldset = field as List<View>
//                empty = (
//                    (fieldset[0] as DatePickerField).value == null
//                        || (fieldset[1] as TimePickerField).value == null
//                    )
//                if (!empty) {
//                    answer = QuestionsDialog.wf.format(
//                        LocalDate.fromCalendarFields((fieldset[0] as DatePickerField).value).toDateTime(
//                            (fieldset[1] as TimePickerField).value
//                        ).toDate()
//                    )
//                }
//            }
        }

        return output
    }

    private fun validate(field: FormField, trimmedValue: String, allAnswersAreOptional: Boolean) {
        try {
            if (!allAnswersAreOptional) {
                field.checkRequired(trimmedValue)
            }
            field.validate(trimmedValue)
        } catch (e: ValidationException) {
            throw QuestionInvalid(R.string.question_input_invalid)
        }
    }

    // TODO: Split into add and clear
    fun addQuestionsError(ctx: Context, f: Any?, label: TextView?, strid: Int) {
        if (f is EditText) {
            f.error = if (strid == 0) null else ctx.getString(strid)
        } else if (f is MutableList<*> && f[0] is EditText) {
            (f as List<EditText>).get(1).error = if (strid == 0) null else ctx.getString(strid)
        } else if (label != null) {
            label.error = if (strid == 0) null else ctx.getString(strid)
        }
    }

    private fun onChange(inputs: List<DialogInput>) {
        checkForWarnings(inputs)
        updateVisibilities()
    }

    private fun onChange() {
        checkForWarnings()
        updateVisibilities()
    }

    private fun checkForWarnings(inputs: List<DialogInput>) {
        for (input in inputs) {
            val identifier = input.field.identifier
            val warningTv = warnings[input.field.identifier]
            if (!input.shouldShow(currentValues)) {
                warningTv?.visibility = View.GONE
                continue
            }

            val output = try {
                extractValues(input.field)
            } catch (e: Throwable) {
                warningTv?.visibility = View.GONE
                continue
            }
            currentValues[identifier] = output

            val warning = input.checkForWarning(output.value)
            if (warning != null) {
                warningTv?.text = warning
                warningTv?.visibility = View.VISIBLE
            } else {
                warningTv?.visibility = View.GONE
            }
        }
    }

    private fun checkForWarnings() {
        checkForWarnings(this.inputs)
    }

    private fun updateVisibilities() {
        for (input in this.inputs) {
//            if (question.dependency == null) continue

            val shouldBeVisible = input.shouldShow(currentValues)

            val identifier = input.field.identifier
            val views = when (input.field) {
//                QuestionType.F, QuestionType.M, QuestionType.W -> {
//                    val l = mutableListOf<View>()
//                    if (fieldViews[question] != null) {
//                        l.addAll(fieldViews[question] as List<View>)
//                        l.add(labels[question]!!)
//                    }
//                    l
//                }
                else -> {
                    listOf(labels[identifier], fieldViews[identifier] as View?)
                }
            }

            val desiredVisibility = if (shouldBeVisible) View.VISIBLE else View.GONE
            for (v in views) {
                if (v == null) {
                    continue
                }

                if (v.visibility != desiredVisibility) {
                    v.visibility = desiredVisibility
                }
            }
        }
    }

    private fun addFields() {
        val llFormFields = v.findViewById<LinearLayout>(R.id.llFormFields)!!
        val ctrlEnterListener: (View, Int, KeyEvent) -> Boolean = fun (_, keyCode, event):Boolean {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.isCtrlPressed) {
                submit()
                return true
            }
            return false
        }

        for (input in inputs) {
            val field = input.field
            val identifier = field.identifier

            val tv = TextView(ctx)
            tv.text = if (!allAnswersAreOptional && field.required) {
                buildSpannedString {
                    append(field.label)
                    append(" ")
                    bold {
                        color(ContextCompat.getColor(context, R.color.pretix_brand_light)) {
                            append("*")
                        }
                    }
                }
            } else {
                field.label
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                tv.setTextAppearance(R.style.TextAppearance_AppCompat_Medium)
            }
            tv.setPadding(0, 16, 0, 4)
            llFormFields.addView(tv)
            labels[identifier] = tv

            val inputValue = input.value
            val defaultValue = input.field.default
            when (field) {
//                QuestionType.TEL -> {
//                    val fieldS = PhoneEditText(ctx)
//                    if (defaultCountry != null) {
//                        fieldS.setDefaultCountry(defaultCountry)
//                    }
//                    if (values?.containsKey(question.identifier) == true && !values[question.identifier].isNullOrBlank()) {
//                        fieldS.setPhoneNumber(values[question.identifier])
//                    } else if (!question.default.isNullOrBlank()) {
//                        fieldS.setPhoneNumber(question.default)
//                    }
//                    fieldViews[question] = fieldS
//                    setters[question.identifier] = { fieldS.setPhoneNumber(it) }
//                    fieldS.setPadding(0, 0, 0, 0)
//                    fieldS.setOnKeyListener(ctrlEnterListener)
//                    fieldS.editText.doAfterTextChanged {
//                        checkForWarnings(listOf(question))
//                    }
//                    llFormFields.addView(fieldS)
//                }
//                QuestionType.EMAIL -> {
//                    val fieldS = EditText(ctx)
//                    if (values?.containsKey(question.identifier) == true && !values[question.identifier].isNullOrBlank()) {
//                        fieldS.setText(values[question.identifier])
//                    } else if (!question.default.isNullOrBlank()) {
//                        fieldS.setText(question.default)
//                    }
//                    setters[question.identifier] = { fieldS.setText(it) }
//                    fieldS.setLines(1)
//                    fieldS.isSingleLine = true
//                    fieldS.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
//                    fieldS.setOnKeyListener(ctrlEnterListener)
//                    fieldS.doAfterTextChanged {
//                        checkForWarnings(listOf(question))
//                    }
//                    fieldViews[question] = fieldS
//                    llFormFields.addView(fieldS)
//                }
                is StringField -> {
                    val fieldS = EditText(ctx)
                    if (!inputValue.isNullOrBlank()) {
                        fieldS.setText(inputValue)
                    } else if (!defaultValue.isNullOrBlank()) {
                        fieldS.setText(defaultValue)
                    }
                    setters[identifier] = { fieldS.setText(it) }
                    fieldS.setLines(1)
                    fieldS.isSingleLine = true
                    fieldS.setOnKeyListener(ctrlEnterListener)
                    fieldS.doAfterTextChanged {
                        onChange(listOf(input))
                    }
                    fieldViews[identifier] = fieldS
                    llFormFields.addView(fieldS)
                }

                is MultiLineStringField -> {
                    val fieldT = EditText(ctx)
                    if (!inputValue.isNullOrBlank()) {
                        fieldT.setText(inputValue)
                    } else if (!defaultValue.isNullOrBlank()) {
                        fieldT.setText(defaultValue)
                    }
                    setters[identifier] = { fieldT.setText(it) }
                    fieldT.setLines(2)
                    fieldT.setOnKeyListener(ctrlEnterListener)
                    fieldT.doAfterTextChanged {
                        onChange(listOf(input))
                    }
                    fieldViews[identifier] = fieldT
                    llFormFields.addView(fieldT)
                }

                is NumberField -> {
                    val fieldN = EditText(ctx)
                    if (!inputValue.isNullOrBlank()) {
                        fieldN.setText(inputValue)
                    } else if (!defaultValue.isNullOrBlank()) {
                        fieldN.setText(defaultValue)
                    }
                    setters[identifier] = { fieldN.setText(it) }
                    fieldN.inputType = InputType.TYPE_CLASS_NUMBER.or(InputType.TYPE_NUMBER_FLAG_DECIMAL).or(
                        InputType.TYPE_NUMBER_FLAG_SIGNED)
                    fieldN.isSingleLine = true
                    fieldN.setLines(1)
                    fieldN.setOnKeyListener(ctrlEnterListener)
                    fieldN.doAfterTextChanged {
                        onChange(listOf(input))
                    }
                    fieldViews[identifier] = fieldN
                    llFormFields.addView(fieldN)
                }

                is BooleanField -> {
                    val fieldB = CheckBox(ctx)
                    fieldB.setText(R.string.yes)
                    if (inputValue != null) {
                        fieldB.isChecked = "True" == inputValue
                    } else if (!defaultValue.isNullOrBlank()) {
                        fieldB.isChecked = "True" == defaultValue
                    }
                    setters[identifier] = { fieldB.isChecked = "True" == it }
                    fieldB.setOnKeyListener(ctrlEnterListener)
                    fieldB.setOnCheckedChangeListener { buttonView, isChecked ->
                        onChange(listOf(input))
                    }
                    fieldViews[identifier] = fieldB
                    llFormFields.addView(fieldB)
                }
//                QuestionType.F -> {
//                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
//                        val tv = TextView(ctx)
//                        tv.text = "Not supported on this Android version or device"
//                        llFormFields.addView(tv)
//                    } else {
//                        val fieldsF = ArrayList<View>()
//
//                        val llInner = LinearLayout(ctx)
//                        llInner.orientation = LinearLayout.HORIZONTAL
//                        llInner.gravity = Gravity.CENTER
//
//                        val circularProgressDrawable = CircularProgressDrawable(ctx)
//                        circularProgressDrawable.strokeWidth = 5f
//                        circularProgressDrawable.centerRadius = 30f
//                        circularProgressDrawable.start()
//
//                        val imgF = ImageView(ctx)
//                        val btnFD = Button(ctx)
//
//                        setters[question.identifier] = {
//                            if (it.isNullOrBlank()) {
//                                imgF.visibility = View.GONE
//                                btnFD.visibility = View.GONE
//                            } else {
//                                imgF.tag = it
//                                imgF.visibility = View.VISIBLE
//                                if (it.startsWith("http") == true && glideLoader != null) {
//                                    if (clonePictures) {
//                                        imgF.tag = null
//                                        Glide.with(context)
//                                            .asFile()
//                                            .load(glideLoader.invoke(it))
//                                            .diskCacheStrategy(DiskCacheStrategy.DATA)
//                                            .listener(object: RequestListener<File> {
//                                                override fun onLoadFailed(
//                                                    e: GlideException?,
//                                                    model: Any?,
//                                                    target: Target<File>?,
//                                                    isFirstResource: Boolean
//                                                ): Boolean {
//                                                    ctx.runOnUiThread {
//                                                        imgF.tag = null
//                                                        btnFD.visibility = View.GONE
//                                                    }
//                                                    return false
//                                                }
//
//                                                override fun onResourceReady(
//                                                    resource: File,
//                                                    model: Any?,
//                                                    target: Target<File>?,
//                                                    dataSource: DataSource?,
//                                                    isFirstResource: Boolean
//                                                ): Boolean {
//                                                    val photoFile = File(
//                                                        getTmpDir(ctx),
//                                                        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
//                                                    )
//                                                    resource.copyTo(photoFile)
//                                                    imgF.tag = "file:///" + photoFile.absolutePath
//                                                    return false
//                                                }
//                                            })
//                                            .submit()
//                                    }
//                                    Glide.with(context)
//                                        .load(glideLoader.invoke(it))
//                                        .placeholder(circularProgressDrawable)
//                                        .diskCacheStrategy(if (clonePictures) DiskCacheStrategy.ALL else DiskCacheStrategy.AUTOMATIC)
//                                        .error(R.drawable.ic_baseline_broken_image_24)
//                                        .into(imgF)
//                                } else if (it.startsWith("file:///") == true) {
//                                    Glide.with(context).load(File(it.substring(7))).into(imgF)
//                                } else {
//                                    Glide.with(context).load(File(it)).into(imgF)
//                                }
//                            }
//                        }
//                        setters[question.identifier]!!(values?.get(question.identifier))
//
//                        imgF.layoutParams = LinearLayout.LayoutParams(160, 120)
//                        fieldsF.add(imgF)
//                        llInner.addView(imgF)
//
//                        val llButtons = LinearLayout(ctx)
//                        llButtons.orientation = LinearLayout.VERTICAL
//                        llButtons.gravity = Gravity.CENTER
//                        llButtons.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
//
//                        val btnF = Button(ctx)
//                        btnF.setText(R.string.take_photo)
//                        btnF.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(ctx.resources, R.drawable.ic_add_a_photo_24, null), null, null, null)
//                        btnF.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
//                        btnF.setOnClickListener {
//                            startTakePhoto(question)
//                        }
//                        fieldsF.add(btnF)
//                        llButtons.addView(btnF)
//
//                        btnFD.setText(R.string.delete_photo)
//                        btnFD.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(ctx.resources, R.drawable.ic_baseline_cancel_24, null), null, null, null)
//                        btnFD.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
//                        btnFD.setOnClickListener {
//                            imgF.tag = null
//                            imgF.visibility = View.GONE
//                            btnFD.visibility = View.GONE
//                            checkForWarnings(listOf(question))
//                        }
//                        fieldsF.add(btnFD)
//                        llButtons.addView(btnFD)
//
//                        llInner.addView(llButtons)
//                        fieldViews[question] = fieldsF
//                        llFormFields.addView(llInner)
//                    }
//                }
                is MultipleChoiceField -> {
                    val fields = ArrayList<CheckBox>()
                    val selected = if (inputValue != null) {
                        inputValue.split(",")
                    } else if (!defaultValue.isNullOrBlank()) {
                        defaultValue.split(",")
                    } else {
                        emptyList()
                    }
                    for (opt in field.options) {
                        val fieldM = CheckBox(ctx)
                        fieldM.text = opt.value
                        fieldM.tag = opt.server_id
                        if (selected.contains(opt.server_id.toString())) {
                            fieldM.isChecked = true
                        }
                        fieldM.setOnKeyListener(ctrlEnterListener)
                        fieldM.setOnCheckedChangeListener { buttonView, isChecked ->
                            onChange(listOf(input))
                        }
                        fields.add(fieldM)
                        llFormFields.addView(fieldM)
                    }
                    setters[identifier] = {
                        for (f in fields) {
                            if (it != null && it.contains((f.tag as Long).toString())) {
                                f.isChecked = true
                            }
                        }
                    }
                    fieldViews[identifier] = fields
                }
//                QuestionType.CC -> {
//                    val fieldC = Spinner(ctx)
//                    fieldC.adapter = CountryAdapter(ctx)
//                    val defaultcc = CountryCode.getByAlpha2Code(Locale.getDefault().country)
//                    setters[question.identifier] = {
//                        val cc = CountryCode.getByAlpha2Code(it)
//                        fieldC.setSelection((fieldC.adapter as CountryAdapter).getIndex(cc
//                            ?: defaultcc))
//                    }
//                    if (values?.containsKey(question.identifier) == true && !values[question.identifier].isNullOrBlank()) {
//                        setters[question.identifier]!!(values[question.identifier])
//                    } else if (!question.default.isNullOrBlank()) {
//                        setters[question.identifier]!!(question.default)
//                    } else {
//                        setters[question.identifier]!!(defaultcc.alpha2)
//                    }
//                    fieldC.setOnKeyListener(ctrlEnterListener)
//                    fieldC.onItemSelectedListener = object : OnItemSelectedListener {
//                        override fun onItemSelected(
//                            p0: AdapterView<*>?,
//                            p1: View?,
//                            p2: Int,
//                            p3: Long
//                        ) {
//                            checkForWarnings(listOf(question))
//                        }
//
//                        override fun onNothingSelected(p0: AdapterView<*>?) {
//                            checkForWarnings(listOf(question))
//                        }
//                    }
//                    fieldViews[question] = fieldC
//                    llFormFields.addView(fieldC)
//                }
//                QuestionType.C -> {
//                    val fieldC = Spinner(ctx)
//                    val opts = question.options!!.toMutableList()
//                    val emptyOpt = QuestionOption(0L, 0, "", "")
//                    opts.add(0, emptyOpt)
//                    fieldC.adapter = OptionAdapter(ctx, opts.filter { it != null } as MutableList<QuestionOption>)
//
//                    setters[question.identifier] = {
//                        var i = 1  // 0 = empty opt
//                        for (opt in question.options) {
//                            if (opt.server_id.toString() == it) {
//                                fieldC.setSelection(i)
//                                break
//                            }
//                            i++
//                        }
//                    }
//
//                    if (values?.containsKey(question.identifier) == true && !values[question.identifier].isNullOrBlank()) {
//                        setters[question.identifier]!!(values[question.identifier])
//                    } else if (!question.default.isNullOrBlank()) {
//                        setters[question.identifier]!!(question.default)
//                    }
//                    fieldC.setOnKeyListener(ctrlEnterListener)
//                    fieldC.onItemSelectedListener = object : OnItemSelectedListener {
//                        override fun onItemSelected(
//                            parent: AdapterView<*>?,
//                            view: View?,
//                            position: Int,
//                            id: Long
//                        ) {
//                            updateDependencyVisibilities()
//                            checkForWarnings(listOf(question))
//                        }
//
//                        override fun onNothingSelected(parent: AdapterView<*>?) {
//                            updateDependencyVisibilities()
//                            checkForWarnings(listOf(question))
//                        }
//                    }
//                    fieldViews[question] = fieldC
//                    llFormFields.addView(fieldC)
//                }
//                QuestionType.D -> {
//                    val fieldD = DatePickerField(ctx, question.valid_date_min, question.valid_date_max)
//                    setters[question.identifier] = {
//                        try {
//                            fieldD.setValue(QuestionsDialog.df.parse(it))
//                        } catch (e: ParseException) {
//                            e.printStackTrace()
//                        }
//                    }
//                    if (values?.containsKey(question.identifier) == true && !values[question.identifier].isNullOrBlank()) {
//                        setters[question.identifier]!!(values[question.identifier])
//                    } else if (!question.default.isNullOrBlank()) {
//                        setters[question.identifier]!!(question.default)
//                    }
//                    fieldD.setOnKeyListener(ctrlEnterListener)
//                    fieldD.doAfterTextChanged {
//                        checkForWarnings(listOf(question))
//                    }
//                    fieldViews[question] = fieldD
//                    llFormFields.addView(fieldD)
//                }
//                QuestionType.H -> {
//                    val fieldH = TimePickerField(ctx)
//                    setters[question.identifier] = {
//                        try {
//                            fieldH.value = LocalTime.fromDateFields(QuestionsDialog.hf.parse(it))
//                        } catch (e: ParseException) {
//                            e.printStackTrace()
//                        }
//                    }
//                    if (values?.containsKey(question.identifier) == true && !values[question.identifier].isNullOrBlank()) {
//                        setters[question.identifier]!!(values[question.identifier])
//                    } else if (!question.default.isNullOrBlank()) {
//                        setters[question.identifier]!!(question.default)
//                    }
//                    fieldH.setOnKeyListener(ctrlEnterListener)
//                    fieldH.doAfterTextChanged {
//                        checkForWarnings(listOf(question))
//                    }
//                    fieldViews[question] = fieldH
//                    llFormFields.addView(fieldH)
//                }
//                QuestionType.W -> {
//                    val fieldsW = ArrayList<EditText>()
//                    val llInner = LinearLayout(ctx)
//                    llInner.orientation = LinearLayout.HORIZONTAL
//
//                    val fieldWD = DatePickerField(ctx, question.valid_datetime_min, question.valid_datetime_max)
//                    fieldWD.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, 1f)
//                    fieldWD.gravity = Gravity.CENTER
//                    fieldWD.setOnKeyListener(ctrlEnterListener)
//                    fieldWD.doAfterTextChanged {
//                        checkForWarnings(listOf(question))
//                    }
//                    fieldsW.add(fieldWD)
//                    llInner.addView(fieldWD)
//
//                    val fieldWH = TimePickerField(ctx)
//                    fieldWH.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, 1f)
//                    fieldWH.gravity = Gravity.CENTER
//                    fieldWH.setOnKeyListener(ctrlEnterListener)
//                    fieldWH.doAfterTextChanged {
//                        checkForWarnings(listOf(question))
//                    }
//                    fieldsW.add(fieldWH)
//                    llInner.addView(fieldWH)
//
//                    setters[question.identifier] = {
//                        try {
//                            fieldWD.setValue(QuestionsDialog.wf.parse(it))
//                            fieldWH.value = LocalTime.fromDateFields(QuestionsDialog.wf.parse(it))
//                        } catch (e: ParseException) {
//                            try {
//                                fieldWD.setValue(QuestionsDialog.wfServer.parse(it))
//                                fieldWH.value = LocalTime.fromDateFields(QuestionsDialog.wfServer.parse(it))
//                            } catch (e: ParseException) {
//                                e.printStackTrace()
//                            }
//                        }
//                    }
//
//                    if (values?.containsKey(question.identifier) == true && !values[question.identifier].isNullOrBlank()) {
//                        setters[question.identifier]!!(values[question.identifier])
//                    } else if (!question.default.isNullOrBlank()) {
//                        setters[question.identifier]!!(question.default)
//                    }
//                    fieldViews[question] = fieldsW
//                    llFormFields.addView(llInner)
//                }


//                else -> TODO()
                else -> {
                    Log.d("FormField", "Field type not implemented $field")
                }
            }

            val warningTv = TextView(ctx)
            warningTv.text = ""
            warningTv.visibility = View.GONE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                warningTv.setTextAppearance(R.style.TextAppearance_AppCompat_Small)
            }
            warningTv.setTextColor(ContextCompat.getColor(ctx, R.color.pretix_brand_orange))
            warningTv.setTypeface(null, Typeface.ITALIC)
            warningTv.setPadding(0, 0, 0, 4)
            llFormFields.addView(warningTv)
            warnings[identifier] = warningTv

            updateVisibilities()
            checkForWarnings()
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        if (savedInstanceState.getString("_waitingForAnswerFor", "") != "") {
            waitingForAnswerFor = inputs.find {
                it.field.identifier.value == savedInstanceState.getString("_waitingForAnswerFor", "")
            }?.field?.identifier
        }
    }

    override fun onSaveInstanceState(): Bundle {
        val state = Bundle()
        // TODO: Currently unused
//        for (question in questions) {
//            try {
//                // force answers are optional, we want to save them all
//                val a = serializeAnswer(question, true)
//                state.putString(a.question.identifier, a.value)
//            } catch (e: QuestionInvalid) {
//                // We do not store invalid answers, that's not perfect, but good enough for now
//            }
//        }
        waitingForAnswerFor?.let {
            state.putString("_waitingForAnswerFor", it.value)
        }
        return state
    }

    // TODO: Use file-specific field type
    private fun startTakePhoto(field: FormField) {
        val intent = Intent(ctx, PhotoCaptureActivity::class.java)
        waitingForAnswerFor = field.identifier
        ctx.startActivityForResult(intent, PhotoCaptureActivity.REQUEST_CODE)
    }

    override fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == PhotoCaptureActivity.REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val filename = data!!.getStringExtra(PhotoCaptureActivity.RESULT_FILENAME)!!
                val views = fieldViews[waitingForAnswerFor] as List<View>
                val imageView = views[0] as ImageView
                imageView.visibility = View.VISIBLE
                imageView.tag = filename
                val btnFD = views[2]
                btnFD.visibility = View.VISIBLE
                Glide.with(context).load(File(filename)).into(imageView)
                checkForWarnings()
            }
            return true
        }
        return false
    }
}

fun showFormDialog(
    header: FormDialogHeader,
    ctx: Activity,
    inputs: List<DialogInput>,
    defaultCountry: String?,
    glideLoader: ((String) -> GlideUrl)? = null,
    onComplete: ((List<DialogOutput>) -> Unit),
    clonePictures: Boolean = false,
    allAnswersAreOptional: Boolean = false,
): FormDialogInterface {
    val dialog = FormDialog(
        header,
        ctx,
        inputs,
        defaultCountry,
        glideLoader,
        onComplete,
        clonePictures,
        allAnswersAreOptional,
    )
    dialog.setCanceledOnTouchOutside(false)
    dialog.show()
    return dialog
}
