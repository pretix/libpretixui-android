package eu.pretix.libpretixui.android.questions

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import com.github.ialokim.phonefield.PhoneEditText
import com.neovisionaries.i18n.CountryCode
import eu.pretix.libpretixsync.check.QuestionType
import eu.pretix.libpretixsync.db.Answer
import eu.pretix.libpretixsync.db.QuestionLike
import eu.pretix.libpretixsync.db.QuestionOption
import eu.pretix.libpretixui.android.PhotoCaptureActivity
import eu.pretix.libpretixui.android.R
import org.joda.time.LocalDate
import org.joda.time.LocalTime
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger


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
    val countries = mutableListOf<CountryCode>();
    countries.addAll(CountryCode.values())
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

fun showQuestionsDialog(ctx: Activity, questions: List<QuestionLike>,
                        values: Map<QuestionLike, String>? = null,
                        defaultCountry: String?,
                        retryHandler: ((MutableList<Answer>) -> Unit)): Dialog {
    val inflater = ctx.layoutInflater
    val fviews = HashMap<QuestionLike, Any>()
    val labels = HashMap<QuestionLike, TextView>()
    val hf = SimpleDateFormat("HH:mm", Locale.US)
    val wf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val view = inflater.inflate(R.layout.dialog_questions, null)
    val llFormFields = view.findViewById<LinearLayout>(R.id.llFormFields)

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun startTakePhoto () {
        val intent = Intent(ctx, PhotoCaptureActivity::class.java)
        ctx.startActivity(intent)
    }

    for (question in questions) {
        val tv = TextView(ctx)
        tv.text = question.question
        llFormFields.addView(tv)
        labels.put(question, tv)

        when (question.type) {
            QuestionType.TEL -> {
                val fieldS = PhoneEditText(ctx)
                if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                    fieldS.setPhoneNumber(values[question])
                }
                fviews[question] = fieldS
                if (defaultCountry != null) {
                    fieldS.setDefaultCountry(defaultCountry)
                }
                fieldS.setPadding(0, 0, 0, 0)
                llFormFields.addView(fieldS)
            }
            QuestionType.EMAIL -> {
                val fieldS = EditText(ctx)
                if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                    fieldS.setText(values[question])
                }
                fieldS.setLines(1)
                fieldS.setSingleLine(true)
                fieldS.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                fviews[question] = fieldS
                llFormFields.addView(fieldS)
            }
            QuestionType.S -> {
                val fieldS = EditText(ctx)
                if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                    fieldS.setText(values[question])
                }
                fieldS.setLines(1)
                fieldS.setSingleLine(true)
                fviews[question] = fieldS
                llFormFields.addView(fieldS)
            }
            QuestionType.T -> {
                val fieldT = EditText(ctx)
                if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                    fieldT.setText(values[question])
                }
                fieldT.setLines(2)
                fviews[question] = fieldT
                llFormFields.addView(fieldT)
            }
            QuestionType.N -> {
                val fieldN = EditText(ctx)
                if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                    fieldN.setText(values[question])
                }
                fieldN.inputType = InputType.TYPE_CLASS_NUMBER.or(InputType.TYPE_NUMBER_FLAG_DECIMAL).or(InputType.TYPE_NUMBER_FLAG_SIGNED)
                fieldN.setSingleLine(true)
                fieldN.setLines(1)
                fviews[question] = fieldN
                llFormFields.addView(fieldN)
            }

            QuestionType.B -> {
                val fieldB = CheckBox(ctx)
                fieldB.setText(R.string.yes)
                if (values?.containsKey(question) == true) {
                    fieldB.isChecked = "True" == values[question]
                }
                fviews[question] = fieldB
                llFormFields.addView(fieldB)
            }
            QuestionType.F -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP || !ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                    val tv = TextView(ctx)
                    tv.text = "Not supported on this Android vearsion or device"
                    llFormFields.addView(tv)
                } else {
                    val btnF = Button(ctx)
                    btnF.setText(R.string.take_photo)
                    btnF.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(ctx.resources, R.drawable.ic_add_a_photo_24, null), null, null, null)
                    btnF.setOnClickListener {
                        startTakePhoto()
                    }
                    llFormFields.addView(btnF)
                }
            }
            QuestionType.M -> {
                val fields = ArrayList<CheckBox>()
                val selected = if (values?.containsKey(question) == true) {
                    values[question]!!.split(",")
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
                    fields.add(field)
                    llFormFields.addView(field)
                }
                fviews[question] = fields
            }
            QuestionType.CC -> {
                val fieldC = Spinner(ctx)
                fieldC.adapter = CountryAdapter(ctx)
                if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                    fieldC.setSelection((fieldC.adapter as CountryAdapter).getIndex(CountryCode.getByAlpha2Code(values[question])))
                } else if (!question.default.isNullOrBlank()) {
                    fieldC.setSelection((fieldC.adapter as CountryAdapter).getIndex(CountryCode.getByAlpha2Code(question.default)))
                } else {
                    val cc = CountryCode.getByAlpha2Code(Locale.getDefault().country)
                    if (cc != null) {
                        fieldC.setSelection((fieldC.adapter as CountryAdapter).getIndex(cc))
                    }
                }

                fviews[question] = fieldC
                llFormFields.addView(fieldC)
            }
            QuestionType.C -> {
                val fieldC = Spinner(ctx)
                val opts = question.options!!.toMutableList()
                val emptyOpt = QuestionOption(0L, 0, "", "")
                opts.add(0, emptyOpt)
                fieldC.adapter = OptionAdapter(ctx, opts.filter { it != null } as MutableList<QuestionOption>)

                if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                    var i = 0
                    for (opt in question.options) {
                        if (opt.server_id.toString() == values[question]) {
                            fieldC.setSelection(i)
                            break
                        }
                        i++
                    }
                }

                fviews[question] = fieldC
                llFormFields.addView(fieldC)
            }
            QuestionType.D -> {
                val fieldD = DatePickerField(ctx)
                if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                    try {
                        fieldD.setValue(df.parse(values[question]))
                    } catch (e: ParseException) {
                        e.printStackTrace()
                    }
                }
                fviews[question] = fieldD
                llFormFields.addView(fieldD)
            }
            QuestionType.H -> {
                val fieldH = TimePickerField(ctx)
                if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                    try {
                        fieldH.value = LocalTime.fromDateFields(hf.parse(values[question]))
                    } catch (e: ParseException) {
                        e.printStackTrace()
                    }
                }
                fviews[question] = fieldH
                llFormFields.addView(fieldH)
            }
            QuestionType.W -> {
                val fieldsW = ArrayList<EditText>()
                val llInner = LinearLayout(ctx)
                llInner.orientation = LinearLayout.HORIZONTAL

                val fieldWD = DatePickerField(ctx)
                fieldWD.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, 1f)
                fieldWD.gravity = Gravity.CENTER
                fieldsW.add(fieldWD)
                llInner.addView(fieldWD)

                val fieldWH = TimePickerField(ctx)
                fieldWH.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, 1f)
                fieldWH.gravity = Gravity.CENTER
                fieldsW.add(fieldWH)
                llInner.addView(fieldWH)

                if (values?.containsKey(question) == true && !values[question].isNullOrBlank()) {
                    try {
                        fieldWD.setValue(wf.parse(values[question]))
                        fieldWH.value = LocalTime.fromDateFields(wf.parse(values[question]))
                    } catch (e: ParseException) {
                        e.printStackTrace()
                    }
                }

                fviews[question] = fieldsW
                llFormFields.addView(llInner)
            }
        }
    }

    val dialog = AlertDialog.Builder(ctx)
            .setView(view)
            .setNegativeButton(R.string.cancel) { dialogInterface, i ->
                dialogInterface.cancel()
            }
            .setPositiveButton(R.string.cont) { dialogInterface, i ->
            }.create()
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    dialog.setOnShowListener {
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.setOnClickListener {

            val answers = ArrayList<Answer>()
            var has_errors = false

            for (question in questions) {
                var answer = ""
                var empty = false
                var invalid = false
                var options = mutableListOf<QuestionOption>()
                var field = fviews.get(question)
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
                        empty = true
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

                if (empty && question.requiresAnswer()) {
                    has_errors = true
                    addQuestionsError(ctx, field, labels[question], R.string.question_input_required)
                } else if (empty) {
                    answers.add(Answer(question, "", options))
                } else if (invalid) {
                    has_errors = true
                    addQuestionsError(ctx, field, labels[question], R.string.question_input_invalid)
                } else {
                    try {
                        question.clean_answer(answer, question.options!!)
                        addQuestionsError(ctx, field, labels[question], 0)
                    } catch (e: QuestionLike.ValidationException) {
                        has_errors = true
                        addQuestionsError(ctx, field, labels[question], R.string.question_input_invalid)
                    }
                    answers.add(Answer(question, answer, options))
                }
            }
            if (!has_errors) {
                dialog.dismiss()
                retryHandler(answers)
            } else {
                Toast.makeText(ctx, R.string.question_validation_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    dialog.show()
    return dialog
}

