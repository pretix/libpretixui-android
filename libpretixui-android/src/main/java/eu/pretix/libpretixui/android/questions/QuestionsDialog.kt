package eu.pretix.libpretixui.android.questions

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.widget.doAfterTextChanged
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.github.ialokim.phonefield.PhoneEditText
import com.neovisionaries.i18n.CountryCode
import eu.pretix.libpretixsync.check.QuestionType
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.db.QuestionLike
import eu.pretix.libpretixsync.db.QuestionLike.ValidationException
import eu.pretix.libpretixsync.db.QuestionOption
import eu.pretix.libpretixui.android.PhotoCaptureActivity
import eu.pretix.libpretixui.android.R
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


fun addQuestionsError(ctx: Context, f: Any?, label: TextView?, strid: Int) {
    if (f is EditText) {
        f.error = if (strid == 0) null else ctx.getString(strid)
    } else if (f is MutableList<*> && f[0] is EditText) {
        (f as List<EditText>).get(1).error = if (strid == 0) null else ctx.getString(strid)
    } else if (label != null) {
        label.error = if (strid == 0) null else ctx.getString(strid)
    }
}

internal class OptionAdapter(context: Context, objects: MutableList<QuestionOption>) : ArrayAdapter<QuestionOption>(context, R.layout.spinneritem_simple, objects)


fun allCountries(): List<CountryCode> {
    val countries = mutableListOf<CountryCode>()
    countries.addAll(CountryCode.values().filter { it.assignment == CountryCode.Assignment.OFFICIALLY_ASSIGNED || it.alpha2 == "XK" })
    countries.sortBy { it.getName() }
    return countries
}

internal class CountryAdapter(context: Context) :
        ArrayAdapter<CountryCode>(context, R.layout.spinneritem_simple, allCountries()) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.spinneritem_simple, parent, false)
        (v as TextView).text = getItem(position)!!.getName()
        return v
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val v = convertView
                ?: LayoutInflater.from(context).inflate(R.layout.spinneritem_simple, parent, false)
        (v as TextView).text = getItem(position)!!.getName()
        return v
    }

    fun getIndex(cc: CountryCode): Int {
        for (i in 0 until count) {
            if (getItem(i) == cc) {
                return i
            }
        }
        return -1
    }
}

interface QuestionsDialogInterface : DialogInterface {
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean

    fun setOnCancelListener(listener: DialogInterface.OnCancelListener?)

    fun isShowing(): Boolean

    fun onRestoreInstanceState(savedInstanceState: Bundle)

    fun onSaveInstanceState(): Bundle
}

class QuestionsDialog(
        val ctx: Activity,
        val questions: List<QuestionLike>,
        val values: Map<QuestionLike, String>? = null,
        val defaultCountry: String?,
        val glideLoader: ((String) -> GlideUrl)? = null,
        val retryHandler: ((MutableList<Answer>) -> Unit),
        val copyFrom: Map<QuestionLike, String>? = null,
        val attendeeName: String? = null,
        val attendeeDOB: String? = null,
        val ticketId: String? = null,
        val ticketType: String? = null,
        val useHardwareScan: Boolean = false,
        val isResumed: Boolean = false,
        val clonePictures: Boolean = false,
        val allAnswersAreOptional: Boolean = false,
) : AlertDialog(ctx), QuestionsDialogInterface {
    companion object {
        val hf = SimpleDateFormat("HH:mm", Locale.US)
        val wf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    private val fieldViews = HashMap<QuestionLike, Any>()
    private val labels = HashMap<QuestionLike, TextView>()
    private val warnings = HashMap<QuestionLike, TextView>()
    private val setters = HashMap<QuestionLike, ((String?) -> Unit)>()
    private var v: View = LayoutInflater.from(context).inflate(R.layout.dialog_questions, null)
    private var waitingForAnswerFor: QuestionLike? = null

    init {
        setView(v)

        if (this.attendeeName.isNullOrBlank()) {
            v.findViewById<TextView>(R.id.tvAttendeeName).visibility = View.GONE
        } else {
            v.findViewById<TextView>(R.id.tvAttendeeName).text = attendeeName
        }
        if (this.attendeeDOB.isNullOrBlank()) {
            v.findViewById<TextView>(R.id.tvAttendeeDOB).visibility = View.GONE
        } else {
            v.findViewById<TextView>(R.id.tvAttendeeDOB).text = attendeeName
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

        setButton(DialogInterface.BUTTON_POSITIVE, ctx.getString(R.string.cont), null as DialogInterface.OnClickListener?)
        setButton(DialogInterface.BUTTON_NEUTRAL, ctx.getString(R.string.cancel)) { p0, p1 ->
            cancel()
        }
        if (copyFrom != null && copyFrom.isNotEmpty()) {
            setButton(DialogInterface.BUTTON_NEUTRAL, ctx.getString(R.string.copy), null as DialogInterface.OnClickListener?)
        }
        setOnShowListener {
            getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                validate()
            }
            if (copyFrom != null && copyFrom.isNotEmpty()) {
                getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    for (cf in copyFrom.entries) {
                        if (setters.containsKey(cf.key)) {
                            setters[cf.key]!!(cf.value)
                        }
                    }
                    if (fieldViews[questions.first()] is EditText && fieldViews[questions.first()] !is DatePickerField) {
                        (fieldViews[questions.first()] as EditText).selectAll()
                        (fieldViews[questions.first()] as EditText).requestFocus()
                    }
                    checkForWarnings()
                }
            }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.isCtrlPressed) {
                    validate()
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
        }

        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        addFields()
    }


    private fun addFields() {
        val llFormFields = v.findViewById<LinearLayout>(R.id.llFormFields)!!
        val ctrlEnterListener: (View, Int, KeyEvent) -> Boolean = fun (_, keyCode, event):Boolean {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.isCtrlPressed) {
                validate()
                return true
            }
            return false
        }
        for (question in questions) {
            val tv = TextView(ctx)
            tv.text = if (!allAnswersAreOptional && question.requiresAnswer()) {
                buildSpannedString {
                    append(question.question)
                    append(" ")
                    bold {
                        color(ContextCompat.getColor(context, R.color.pretix_brand_light)) {
                            append("*")
                        }
                    }
                }
            } else {
                question.question
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                tv.setTextAppearance(R.style.TextAppearance_AppCompat_Medium)
            }
            tv.setPadding(0, 16, 0, 4)
            llFormFields.addView(tv)
            labels[question] = tv

            when (question.type) {
                QuestionType.TEL -> {
                    val fieldS = PhoneEditText(ctx)
                    if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                        fieldS.setPhoneNumber(values[question])
                    } else if (!question.default.isNullOrBlank()) {
                        fieldS.setPhoneNumber(question.default)
                    }
                    fieldViews[question] = fieldS
                    setters[question] = { fieldS.setPhoneNumber(it) }
                    if (defaultCountry != null) {
                        fieldS.setDefaultCountry(defaultCountry)
                    }
                    fieldS.setPadding(0, 0, 0, 0)
                    fieldS.setOnKeyListener(ctrlEnterListener)
                    fieldS.editText.doAfterTextChanged {
                        checkForWarnings(listOf(question))
                    }
                    llFormFields.addView(fieldS)
                }
                QuestionType.EMAIL -> {
                    val fieldS = EditText(ctx)
                    if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                        fieldS.setText(values[question])
                    } else if (!question.default.isNullOrBlank()) {
                        fieldS.setText(question.default)
                    }
                    setters[question] = { fieldS.setText(it) }
                    fieldS.setLines(1)
                    fieldS.isSingleLine = true
                    fieldS.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    fieldS.setOnKeyListener(ctrlEnterListener)
                    fieldS.doAfterTextChanged {
                        checkForWarnings(listOf(question))
                    }
                    fieldViews[question] = fieldS
                    llFormFields.addView(fieldS)
                }
                QuestionType.S -> {
                    val fieldS = EditText(ctx)
                    if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                        fieldS.setText(values[question])
                    } else if (!question.default.isNullOrBlank()) {
                        fieldS.setText(question.default)
                    }
                    setters[question] = { fieldS.setText(it) }
                    fieldS.setLines(1)
                    fieldS.isSingleLine = true
                    fieldS.setOnKeyListener(ctrlEnterListener)
                    fieldS.doAfterTextChanged {
                        checkForWarnings(listOf(question))
                    }
                    fieldViews[question] = fieldS
                    llFormFields.addView(fieldS)
                }
                QuestionType.T -> {
                    val fieldT = EditText(ctx)
                    if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                        fieldT.setText(values[question])
                    } else if (!question.default.isNullOrBlank()) {
                        fieldT.setText(question.default)
                    }
                    setters[question] = { fieldT.setText(it) }
                    fieldT.setLines(2)
                    fieldT.setOnKeyListener(ctrlEnterListener)
                    fieldT.doAfterTextChanged {
                        checkForWarnings(listOf(question))
                    }
                    fieldViews[question] = fieldT
                    llFormFields.addView(fieldT)
                }
                QuestionType.N -> {
                    val fieldN = EditText(ctx)
                    if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                        fieldN.setText(values[question])
                    } else if (!question.default.isNullOrBlank()) {
                        fieldN.setText(question.default)
                    }
                    setters[question] = { fieldN.setText(it) }
                    fieldN.inputType = InputType.TYPE_CLASS_NUMBER.or(InputType.TYPE_NUMBER_FLAG_DECIMAL).or(InputType.TYPE_NUMBER_FLAG_SIGNED)
                    fieldN.isSingleLine = true
                    fieldN.setLines(1)
                    fieldN.setOnKeyListener(ctrlEnterListener)
                    fieldN.doAfterTextChanged {
                        checkForWarnings(listOf(question))
                    }
                    fieldViews[question] = fieldN
                    llFormFields.addView(fieldN)
                }

                QuestionType.B -> {
                    val fieldB = CheckBox(ctx)
                    fieldB.setText(R.string.yes)
                    if (values?.containsKey(question) == true) {
                        fieldB.isChecked = "True" == values[question]
                    } else if (!question.default.isNullOrBlank()) {
                        fieldB.isChecked = "True" == question.default
                    }
                    setters[question] = { fieldB.isChecked = "True" == it }
                    fieldB.setOnKeyListener(ctrlEnterListener)
                    fieldB.setOnCheckedChangeListener { buttonView, isChecked ->
                        updateDependencyVisibilities()
                        checkForWarnings(listOf(question))
                    }
                    fieldViews[question] = fieldB
                    llFormFields.addView(fieldB)
                }
                QuestionType.F -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        val tv = TextView(ctx)
                        tv.text = "Not supported on this Android version or device"
                        llFormFields.addView(tv)
                    } else {
                        val fieldsF = ArrayList<View>()

                        val llInner = LinearLayout(ctx)
                        llInner.orientation = LinearLayout.HORIZONTAL
                        llInner.gravity = Gravity.CENTER

                        val circularProgressDrawable = CircularProgressDrawable(ctx)
                        circularProgressDrawable.strokeWidth = 5f
                        circularProgressDrawable.centerRadius = 30f
                        circularProgressDrawable.start()

                        val imgF = ImageView(ctx)
                        val btnFD = Button(ctx)

                        setters[question] = {
                            if (it.isNullOrBlank()) {
                                imgF.visibility = View.GONE
                                btnFD.visibility = View.GONE
                            } else {
                                imgF.tag = it
                                imgF.visibility = View.VISIBLE
                                if (it.startsWith("http") == true && glideLoader != null) {
                                    if (clonePictures) {
                                        imgF.tag = null
                                        Glide.with(context)
                                            .asFile()
                                            .load(glideLoader.invoke(it))
                                            .diskCacheStrategy(DiskCacheStrategy.DATA)
                                            .listener(object: RequestListener<File>{
                                                override fun onLoadFailed(
                                                    e: GlideException?,
                                                    model: Any?,
                                                    target: Target<File>?,
                                                    isFirstResource: Boolean
                                                ): Boolean {
                                                    ctx.runOnUiThread {
                                                        imgF.tag = null
                                                        btnFD.visibility = View.GONE
                                                    }
                                                    return false
                                                }

                                                override fun onResourceReady(
                                                    resource: File,
                                                    model: Any?,
                                                    target: Target<File>?,
                                                    dataSource: DataSource?,
                                                    isFirstResource: Boolean
                                                ): Boolean {
                                                    val photoFile = File(
                                                        getTmpDir(ctx),
                                                        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
                                                    )
                                                    resource.copyTo(photoFile)
                                                    imgF.tag = "file:///" + photoFile.absolutePath
                                                    return false
                                                }
                                            })
                                            .submit()
                                    }
                                    Glide.with(context)
                                            .load(glideLoader.invoke(it))
                                            .placeholder(circularProgressDrawable)
                                            .diskCacheStrategy(if (clonePictures) DiskCacheStrategy.ALL else DiskCacheStrategy.AUTOMATIC)
                                            .error(R.drawable.ic_baseline_broken_image_24)
                                            .into(imgF)
                                } else if (it.startsWith("file:///") == true) {
                                    Glide.with(context).load(File(it.substring(7))).into(imgF)
                                } else {
                                    Glide.with(context).load(File(it)).into(imgF)
                                }
                            }
                        }
                        setters[question]!!(values?.get(question))

                        imgF.layoutParams = LinearLayout.LayoutParams(160, 120)
                        fieldsF.add(imgF)
                        llInner.addView(imgF)

                        val llButtons = LinearLayout(ctx)
                        llButtons.orientation = LinearLayout.VERTICAL
                        llButtons.gravity = Gravity.CENTER
                        llButtons.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

                        val btnF = Button(ctx)
                        btnF.setText(R.string.take_photo)
                        btnF.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(ctx.resources, R.drawable.ic_add_a_photo_24, null), null, null, null)
                        btnF.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        btnF.setOnClickListener {
                            startTakePhoto(question)
                        }
                        fieldsF.add(btnF)
                        llButtons.addView(btnF)

                        btnFD.setText(R.string.delete_photo)
                        btnFD.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(ctx.resources, R.drawable.ic_baseline_cancel_24, null), null, null, null)
                        btnFD.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        btnFD.setOnClickListener {
                            imgF.tag = null
                            imgF.visibility = View.GONE
                            btnFD.visibility = View.GONE
                            checkForWarnings(listOf(question))
                        }
                        fieldsF.add(btnFD)
                        llButtons.addView(btnFD)

                        llInner.addView(llButtons)
                        fieldViews[question] = fieldsF
                        llFormFields.addView(llInner)
                    }
                }
                QuestionType.M -> {
                    val fields = ArrayList<CheckBox>()
                    val selected = if (values?.containsKey(question) == true) {
                        values[question]!!.split(",")
                    } else if (!question.default.isNullOrBlank()) {
                        question.default.split(",")
                    } else {
                        emptyList<String>()
                    }
                    for (opt in question.options!!) {
                        val field = CheckBox(ctx)
                        field.text = opt!!.value
                        field.tag = opt
                        if (selected.contains(opt.server_id.toString())) {
                            field.isChecked = true
                        }
                        field.setOnKeyListener(ctrlEnterListener)
                        field.setOnCheckedChangeListener { buttonView, isChecked ->
                            updateDependencyVisibilities()
                            checkForWarnings(listOf(question))
                        }
                        fields.add(field)
                        llFormFields.addView(field)
                    }
                    setters[question] = {
                        for (f in fields) {
                            if (it != null && it.contains((f.tag as QuestionOption).server_id.toString())) {
                                f.isChecked = true
                            }
                        }
                    }
                    fieldViews[question] = fields
                }
                QuestionType.CC -> {
                    val fieldC = Spinner(ctx)
                    fieldC.adapter = CountryAdapter(ctx)
                    val defaultcc = CountryCode.getByAlpha2Code(Locale.getDefault().country)
                    setters[question] = {
                        val cc = CountryCode.getByAlpha2Code(it)
                        fieldC.setSelection((fieldC.adapter as CountryAdapter).getIndex(cc
                                ?: defaultcc))
                    }
                    if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                        setters[question]!!(values[question])
                    } else if (!question.default.isNullOrBlank()) {
                        setters[question]!!(question.default)
                    } else {
                        setters[question]!!(defaultcc.alpha2)
                    }
                    fieldC.setOnKeyListener(ctrlEnterListener)
                    fieldC.onItemSelectedListener = object : OnItemSelectedListener {
                        override fun onItemSelected(
                            p0: AdapterView<*>?,
                            p1: View?,
                            p2: Int,
                            p3: Long
                        ) {
                            checkForWarnings(listOf(question))
                        }

                        override fun onNothingSelected(p0: AdapterView<*>?) {
                            checkForWarnings(listOf(question))
                        }
                    }
                    fieldViews[question] = fieldC
                    llFormFields.addView(fieldC)
                }
                QuestionType.C -> {
                    val fieldC = Spinner(ctx)
                    val opts = question.options!!.toMutableList()
                    val emptyOpt = QuestionOption(0L, 0, "", "")
                    opts.add(0, emptyOpt)
                    fieldC.adapter = OptionAdapter(ctx, opts.filter { it != null } as MutableList<QuestionOption>)

                    setters[question] = {
                        var i = 1  // 0 = empty opt
                        for (opt in question.options) {
                            if (opt.server_id.toString() == it) {
                                fieldC.setSelection(i)
                                break
                            }
                            i++
                        }
                    }

                    if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                        setters[question]!!(values[question])
                    } else if (!question.default.isNullOrBlank()) {
                        setters[question]!!(question.default)
                    }
                    fieldC.setOnKeyListener(ctrlEnterListener)
                    fieldC.onItemSelectedListener = object : OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long
                        ) {
                            updateDependencyVisibilities()
                            checkForWarnings(listOf(question))
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {
                            updateDependencyVisibilities()
                            checkForWarnings(listOf(question))
                        }
                    }
                    fieldViews[question] = fieldC
                    llFormFields.addView(fieldC)
                }
                QuestionType.D -> {
                    val fieldD = DatePickerField(ctx, question.valid_date_min, question.valid_date_max)
                    setters[question] = {
                        try {
                            fieldD.setValue(df.parse(it))
                        } catch (e: ParseException) {
                            e.printStackTrace()
                        }
                    }
                    if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                        setters[question]!!(values[question])
                    } else if (!question.default.isNullOrBlank()) {
                        setters[question]!!(question.default)
                    }
                    fieldD.setOnKeyListener(ctrlEnterListener)
                    fieldD.doAfterTextChanged {
                        checkForWarnings(listOf(question))
                    }
                    fieldViews[question] = fieldD
                    llFormFields.addView(fieldD)
                }
                QuestionType.H -> {
                    val fieldH = TimePickerField(ctx)
                    setters[question] = {
                        try {
                            fieldH.value = LocalTime.fromDateFields(hf.parse(it))
                        } catch (e: ParseException) {
                            e.printStackTrace()
                        }
                    }
                    if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                        setters[question]!!(values[question])
                    } else if (!question.default.isNullOrBlank()) {
                        setters[question]!!(question.default)
                    }
                    fieldH.setOnKeyListener(ctrlEnterListener)
                    fieldH.doAfterTextChanged {
                        checkForWarnings(listOf(question))
                    }
                    fieldViews[question] = fieldH
                    llFormFields.addView(fieldH)
                }
                QuestionType.W -> {
                    val fieldsW = ArrayList<EditText>()
                    val llInner = LinearLayout(ctx)
                    llInner.orientation = LinearLayout.HORIZONTAL

                    val fieldWD = DatePickerField(ctx, question.valid_datetime_min, question.valid_datetime_max)
                    fieldWD.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, 1f)
                    fieldWD.gravity = Gravity.CENTER
                    fieldWD.setOnKeyListener(ctrlEnterListener)
                    fieldWD.doAfterTextChanged {
                        checkForWarnings(listOf(question))
                    }
                    fieldsW.add(fieldWD)
                    llInner.addView(fieldWD)

                    val fieldWH = TimePickerField(ctx)
                    fieldWH.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, 1f)
                    fieldWH.gravity = Gravity.CENTER
                    fieldWH.setOnKeyListener(ctrlEnterListener)
                    fieldWH.doAfterTextChanged {
                        checkForWarnings(listOf(question))
                    }
                    fieldsW.add(fieldWH)
                    llInner.addView(fieldWH)

                    setters[question] = {
                        try {
                            fieldWD.setValue(wf.parse(it))
                            fieldWH.value = LocalTime.fromDateFields(wf.parse(it))
                        } catch (e: ParseException) {
                            e.printStackTrace()
                        }
                    }

                    if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                        setters[question]!!(values[question])
                    } else if (!question.default.isNullOrBlank()) {
                        setters[question]!!(question.default)
                    }
                    fieldViews[question] = fieldsW
                    llFormFields.addView(llInner)
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
            warnings[question] = warningTv

            updateDependencyVisibilities()
            checkForWarnings()
        }
    }

    class QuestionInvalid(val msgid: Int) : Exception()

    private fun serializeAnswer(question: QuestionLike, allAnswersAreOptional: Boolean): Answer {
        var answer = ""
        var empty = false
        var invalid = false
        val options = mutableListOf<QuestionOption>()
        val field = fieldViews[question]
        when (question.type) {
            QuestionType.S, QuestionType.T, QuestionType.EMAIL -> {
                answer = (field as EditText).text.toString()
                empty = answer.trim() == ""
            }
            QuestionType.TEL -> {
                answer = (field as PhoneEditText).phoneNumberE164 ?: ""
                empty = answer.trim() == ""
                invalid = !(field as PhoneEditText).isValid()
            }
            QuestionType.N -> {
                answer = (field as EditText).text.toString()
                empty = answer.trim() == ""
            }
            QuestionType.B -> {
                answer = if ((field as CheckBox).isChecked) "True" else "False"
                empty = answer == "False"
            }
            QuestionType.F -> {
                val fieldset = field as List<View>
                answer = if (fieldset[0].tag is String && (fieldset[0].tag as String).contains("://"))
                    fieldset[0].tag as String
                else if (fieldset[0].tag != null)
                    "file://${fieldset[0].tag as String}"
                else
                    ""
                empty = answer.trim() == ""
            }
            QuestionType.M -> {
                empty = true
                val aw = StringBuilder()
                for (f in (field as List<CheckBox>)) {
                    if (f.isChecked) {
                        if (!empty) {
                            aw.append(",")
                        }
                        aw.append((f.tag as QuestionOption).server_id)
                        options.add(f.tag as QuestionOption)
                        empty = false
                    }
                }
                answer = aw.toString()
            }
            QuestionType.CC -> {
                val opt = ((field as Spinner).selectedItem as CountryCode)
                answer = opt.alpha2
            }
            QuestionType.C -> {
                val opt = ((field as Spinner).selectedItem as QuestionOption)
                if (opt.server_id == 0L) {
                    empty = true
                } else {
                    answer = opt.server_id.toString()
                    options.add(opt)
                }
            }
            QuestionType.D -> {
                empty = ((field as DatePickerField).value == null)
                if (!empty) {
                    answer = df.format(field.value!!.time)
                }
            }
            QuestionType.H -> {
                empty = ((field as TimePickerField).value == null)
                if (!empty) {
                    answer = hf.format(field.value!!.toDateTimeToday().toDate())
                }
            }
            QuestionType.W -> {
                val fieldset = field as List<View>
                empty = (
                        (fieldset[0] as DatePickerField).value == null
                                || (fieldset[1] as TimePickerField).value == null
                        )
                if (!empty) {
                    answer = wf.format(
                            LocalDate.fromCalendarFields((fieldset[0] as DatePickerField).value).toDateTime(
                                    (fieldset[1] as TimePickerField).value
                            ).toDate()
                    )
                }
            }
        }
        if (!allAnswersAreOptional && empty && question.requiresAnswer()) {
            throw QuestionInvalid(R.string.question_input_required)
        } else if (empty) {
            return Answer(question, "", options)
        } else if (invalid) {
            throw QuestionInvalid(R.string.question_input_invalid)
        } else {
            try {
                question.clean_answer(answer, question.options!!, allAnswersAreOptional)
            } catch (e: QuestionLike.ValidationException) {
                throw QuestionInvalid(R.string.question_input_invalid)
            }
            return Answer(question, answer, options)
        }
    }

    private fun questionIsVisible(question: QuestionLike): Boolean {
        if (question.dependency == null) {
            return true
        }
        if (question.dependency.dependency !== null && !questionIsVisible(question.dependency)) {
            return false
        }
        val field = fieldViews[question.dependency] ?: return false
        when (question.dependency.type) {
            QuestionType.C -> {
                val opt = ((field as Spinner).selectedItem as QuestionOption)
                if (question.dependencyValues.contains(opt.identifier)) {
                    return true
                }
                return false
            }
            QuestionType.M -> {
                for (f in (field as List<CheckBox>)) {
                    if (f.isChecked) {
                        if (question.dependencyValues.contains((f.tag as QuestionOption).identifier)) {
                            return true
                        }
                    }
                }
                return false
            }
            QuestionType.B -> {
                val value = (field as CheckBox).isChecked
                return (question.dependencyValues.contains("True") && value) || (question.dependencyValues.contains("False") && !value)
            }
            else -> return false
        }
    }

    private fun checkForWarnings(questions: List<QuestionLike>? = null) {
        for (question in (questions ?: this.questions)) {
            val warningTv = warnings[question]
            if (!questionIsVisible(question)) {
                warningTv?.visibility = View.GONE
                continue
            }

            val answer = try {
                serializeAnswer(question, allAnswersAreOptional)
            } catch (e: Throwable) {
                warningTv?.visibility = View.GONE
                continue
            }

            try {
                question.warn_answer(answer.value, question.options!!, allAnswersAreOptional)
                warningTv?.visibility = View.GONE
            } catch (e: ValidationException) {
                warningTv?.text = e.message
                warningTv?.visibility = View.VISIBLE
            }
        }
    }

    private fun updateDependencyVisibilities() {
        for (question in questions) {
            if (question.dependency == null) continue

            val shouldBeVisible = questionIsVisible(question)

            val fields = when (question.type) {
                QuestionType.T -> {
                    listOf(labels[question], fieldViews[question] as View?)
                }
                QuestionType.F, QuestionType.M, QuestionType.W -> {
                    val l = mutableListOf<View>()
                    if (fieldViews[question] != null) {
                        l.addAll(fieldViews[question] as List<View>)
                        l.add(labels[question]!!)
                    }
                    l
                }
                else -> {
                    listOf(labels[question], fieldViews[question] as View?)
                }
            }

            val desiredVisibility = if (shouldBeVisible) View.VISIBLE else View.GONE
            if (fields.any { it != null && it.visibility != desiredVisibility }) {
                for (f in fields) {
                    f?.visibility = desiredVisibility
                }
            }
        }
    }

    private fun validate() {
        val answers = ArrayList<Answer>()
        var has_errors = false

        for (question in questions) {
            val field = fieldViews[question]
            if (!questionIsVisible(question)) {
                continue
            }

            try {
                answers.add(serializeAnswer(question, allAnswersAreOptional))
                addQuestionsError(ctx, field, labels[question], 0)
            } catch (e: QuestionInvalid) {
                addQuestionsError(ctx, field, labels[question], e.msgid)
                has_errors = true
            }
        }
        if (!has_errors) {
            dismiss()
            retryHandler(answers)
        } else {
            Toast.makeText(ctx, R.string.question_validation_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
       if (savedInstanceState.getString("_waitingForAnswerFor", "") != "") {
           waitingForAnswerFor = questions.find { it.identifier == savedInstanceState.getString("_waitingForAnswerFor", "") }
       }
    }

    override fun onSaveInstanceState(): Bundle {
        val state = Bundle()
        for (question in questions) {
            try {
                // force answers are optional, we want to save them all
                val a = serializeAnswer(question, true)
                state.putString(a.question.identifier, a.value)
            } catch (e: QuestionInvalid) {
                // We do not store invalid answers, that's not perfect, but good enough for now
            }
        }
        if (waitingForAnswerFor != null) {
            state.putString("_waitingForAnswerFor", waitingForAnswerFor!!.identifier)
        }
        return state
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startTakePhoto(question: QuestionLike) {
        val intent = Intent(ctx, PhotoCaptureActivity::class.java)
        waitingForAnswerFor = question
        ctx.startActivityForResult(intent, PhotoCaptureActivity.REQUEST_CODE)
    }

    override fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && requestCode == PhotoCaptureActivity.REQUEST_CODE) {
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

fun showQuestionsDialog(
        ctx: Activity,
        questions: List<QuestionLike>,
        values: Map<QuestionLike, String>? = null,
        defaultCountry: String?,
        glideLoader: ((String) -> GlideUrl)? = null,
        retryHandler: ((MutableList<Answer>) -> Unit),
        copyFrom: Map<QuestionLike, String>? = null,
        attendeeName: String? = null,
        attendeeDOB: String? = null,
        ticketId: String? = null,
        ticketType: String? = null,
        useHardwareScan: Boolean = false,
        isResumed: Boolean = false,
        clonePictures: Boolean = false,
        allAnswersAreOptional: Boolean = false,
): QuestionsDialogInterface {
    val dialog = QuestionsDialog(
            ctx,
            questions,
            values,
            defaultCountry,
            glideLoader,
            retryHandler,
            copyFrom,
            attendeeName,
            attendeeDOB,
            ticketId,
            ticketType,
            useHardwareScan,
            isResumed,
            clonePictures,
            allAnswersAreOptional
    )
    dialog.setCanceledOnTouchOutside(false)
    dialog.show()
    return dialog
}

