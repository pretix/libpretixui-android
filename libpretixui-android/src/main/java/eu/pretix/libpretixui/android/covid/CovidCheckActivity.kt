package eu.pretix.libpretixui.android.covid

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import eu.pretix.libpretixui.android.R
import eu.pretix.libpretixui.android.databinding.ActivityCovidCheckBinding
import org.joda.time.LocalDate

class CovidCheckActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = DataBindingUtil.setContentView<ActivityCovidCheckBinding>(this, R.layout.activity_covid_check)

        binding.birthdate = LocalDate.parse("1999-01-01")
        binding.name = "Max Mustermann"
        binding.hasHardwareScanner = true
        binding.settings = SAMPLE_SETTINGS

    }
}