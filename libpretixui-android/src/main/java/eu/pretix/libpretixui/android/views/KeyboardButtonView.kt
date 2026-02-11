package eu.pretix.libpretixui.android.views

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import eu.pretix.libpretixui.android.R


// (c) 2015 OrangeGangsters
// MIT License
// https://github.com/omadahealth/LolliPin/blob/0c523dfb7e9ee5dfcf37ad047cbb970ccd8794fb/lib/src/main/java/com/github/omadahealth/lollipin/lib/views/KeyboardButtonView.java

class KeyboardButtonView @JvmOverloads constructor(private val mContext: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : RelativeLayout(mContext, attrs, defStyleAttr) {

    private var mRippleView: View? = null

    init {
        initializeView(attrs, defStyleAttr)
    }

    private fun initializeView(attrs: AttributeSet?, defStyleAttr: Int) {
        if (attrs != null && !isInEditMode) {
            val attributes = mContext.getTheme().obtainStyledAttributes(attrs, R.styleable.KeyboardButtonView,
                    defStyleAttr, 0)
            val text = attributes.getString(R.styleable.KeyboardButtonView_lp_keyboard_button_text)
            val image = attributes.getDrawable(R.styleable.KeyboardButtonView_lp_keyboard_button_image)

            attributes.recycle()

            val inflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = inflater.inflate(R.layout.view_keyboard_button, this) as KeyboardButtonView

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.focusable = NOT_FOCUSABLE
            }

            if (text != null) {
                val textView = view.findViewById(R.id.keyboard_button_textview) as TextView
                textView?.setText(text)
            }
            if (image != null) {
                val imageView = view.findViewById(R.id.keyboard_button_imageview) as ImageView
                if (imageView != null) {
                    imageView!!.setImageDrawable(image)
                    imageView!!.setVisibility(VISIBLE)
                }
            }

            mRippleView = view.findViewById(R.id.pin_code_keyboard_button_ripple) as View
            if (mRippleView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mRippleView!!.focusable = NOT_FOCUSABLE
                }
            }
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        onTouchEvent(event)
        return false
    }
}
