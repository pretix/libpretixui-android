package eu.pretix.libpretixui.android.covid

import de.rki.covpass.sdk.cert.models.Recovery
import de.rki.covpass.sdk.cert.models.TestCert
import de.rki.covpass.sdk.cert.models.Vaccination
import eu.pretix.jsonlogic.JsonLogic
import eu.pretix.jsonlogic.truthy
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// "3G"
val sampleRuleSingleFactor = """
    {"or" : [
        { "var" : "VACC" },
        { "var" : "CURED" },
        { "var" : "TESTED_PCR" },
        { "var" : "TESTED_AG_UNKNOWN" },
        { "var" : "OTHER" }
    ] }
""".trimIndent()

// "2G"
val sampleRuleImmunizedAndTested = """
    {"and" : [
        {"or" : [
            { "var" : "VACC" },
            { "var" : "CURED" },
            { "var" : "OTHER" }
        ]},
        {"or" : [
            { "var" : "TESTED_PCR" },
            { "var" : "TESTED_AG_UNKNOWN" },
            { "var" : "OTHER" }
        ]},
    ] }
""".trimIndent()

// https://www.baden-wuerttemberg.de/de/service/aktuelle-infos-zu-corona/aktuelle-corona-verordnung-des-landes-baden-wuerttemberg/
// (1a) Soweit in Teil 2 der Zutritt zu den dort genannten Einrichtungen oder Angeboten im Rahmen der verfügbaren und zulässigen Kapazitäten nur für immunisierte Personen nach Vorlage eines Antigen- oder PCR-Testnachweises gestattet ist, gilt dies nicht für
//
//    geimpfte Personen, deren Nachweis hinsichtlich des Vorliegens einer vollständigen Schutzimpfung nicht länger als 6 Monate zurückliegt,
//    genesene Personen,
//    geimpfte oder genesene Personen, die eine Auffrischungsimpfung erhalten haben, oder
//    Personen, für die keine Empfehlung der Ständigen Impfkommission hinsichtlich einer Auffrischungsimpfung besteht.
val sampleRuleBadenWuerttembergDecember2021 = """
    {"or" : [
    
        {"and" : [
            { "var" : "VACC" },
            { "!=" : [ { "var": "VACC_occurence_days_since" }, null ] },
            { "<=" : [ { "var": "VACC_occurence_days_since" }, 180 ] }
        ]},
    
        { "var" : "CURED" },
    
        {"and" : [
            { "var" : "VACC" },
            { "var": "VACC_isBooster" }
        ]},
    
        {"and" : [
            { "var" : "VACC" },
            {"or" : [
                { "var" : "TESTED_PCR" },
                { "var" : "TESTED_AG_UNKNOWN" }
            ]}
        ]},
        
        { "var" : "OTHER" }
    ] }
""".trimIndent()


class CombinationRules(val logic: String) {
    fun isValid(results: Map<Proof, ScanResult>): Boolean {
        val data = mutableMapOf<String, Any?>()

        if (results.containsKey(Proof.VACC)) {
            data.put("VACC", true)
            val cert = results[Proof.VACC]!!.dgcEntry as? Vaccination
            data.put("VACC_targetDisease", cert?.targetDisease)
            data.put("VACC_vaccineCode", cert?.vaccineCode)
            data.put("VACC_product", cert?.product)
            data.put("VACC_manufacturer", cert?.manufacturer)
            data.put("VACC_doseNumber", cert?.doseNumber)
            data.put("VACC_totalSerialDoses", cert?.totalSerialDoses)
            data.put("VACC_occurence_days_since", if (cert == null) null else ChronoUnit.DAYS.between(cert.occurrence, LocalDate.now()))
            data.put("VACC_country", cert?.country)
            data.put("VACC_certificateIssuer", cert?.certificateIssuer)
            data.put("VACC_isComplete", cert?.isComplete)
            data.put("VACC_isCompleteSingleDose", cert?.isCompleteSingleDose)
            data.put("VACC_isBooster", cert?.isBooster)
            data.put("VACC_hasFullProtectionAfterRecovery", cert?.hasFullProtectionAfterRecovery)
            data.put("VACC_hasFullProtection", cert?.hasFullProtection)
        } else {
            data.put("VACC", false)
        }

        if (results.containsKey(Proof.CURED)) {
            data.put("CURED", true)
            val cert = results[Proof.CURED]!!.dgcEntry as? Recovery
            data.put("CURED_targetDisease", cert?.targetDisease)
            data.put("CURED_firstResult_days_since", if (cert == null) null else ChronoUnit.DAYS.between(cert.firstResult, LocalDate.now()))
            data.put("CURED_validFrom_days_since", if (cert == null) null else ChronoUnit.DAYS.between(cert.validFrom, LocalDate.now()))
            data.put("CURED_validUntil_days_until", if (cert == null) null else ChronoUnit.DAYS.between(LocalDate.now(), cert.validUntil))
            data.put("CURED_country", cert?.country)
            data.put("CURED_certificateIssuer", cert?.certificateIssuer)
        } else {
            data.put("CURED", false)
        }

        if (results.containsKey(Proof.TESTED_PCR)) {
            data.put("TESTED_PCR", true)
            val cert = results[Proof.TESTED_PCR]!!.dgcEntry as? TestCert
            data.put("TESTED_PCR_targetDisease", cert?.targetDisease)
            data.put("TESTED_PCR_testType", cert?.testType)
            data.put("TESTED_PCR_testName", cert?.testName)
            data.put("TESTED_PCR_manufacturer", cert?.manufacturer)
            data.put("TESTED_PCR_sampleCollection_hours_since", if (cert == null) null else ChronoUnit.HOURS.between(cert.sampleCollection, ZonedDateTime.now()))
            data.put("TESTED_PCR_testResult", cert?.testResult)
            data.put("TESTED_PCR_testingCenter", cert?.testingCenter)
            data.put("TESTED_PCR_country", cert?.country)
            data.put("TESTED_PCR_certificateIssuer", cert?.certificateIssuer)
            data.put("TESTED_PCR_isPositive", cert?.isPositive)
        } else {
            data.put("TESTED_PCR", false)
        }

        if (results.containsKey(Proof.TESTED_AG_UNKNOWN)) {
            data.put("TESTED_AG_UNKNOWN", true)
            val cert = results[Proof.TESTED_AG_UNKNOWN]!!.dgcEntry as? TestCert
            data.put("TESTED_AG_UNKNOWN_targetDisease", cert?.targetDisease)
            data.put("TESTED_AG_UNKNOWN_testType", cert?.testType)
            data.put("TESTED_AG_UNKNOWN_testName", cert?.testName)
            data.put("TESTED_AG_UNKNOWN_manufacturer", cert?.manufacturer)
            data.put("TESTED_AG_UNKNOWN_sampleCollection_hours_since", if (cert == null) null else ChronoUnit.HOURS.between(cert.sampleCollection, ZonedDateTime.now()))
            data.put("TESTED_AG_UNKNOWN_testResult", cert?.testResult)
            data.put("TESTED_AG_UNKNOWN_testingCenter", cert?.testingCenter)
            data.put("TESTED_AG_UNKNOWN_country", cert?.country)
            data.put("TESTED_AG_UNKNOWN_certificateIssuer", cert?.certificateIssuer)
            data.put("TESTED_AG_UNKNOWN_isPositive", cert?.isPositive)
        } else {
            data.put("TESTED_AG_UNKNOWN", false)
        }

        data.put("OTHER", results.containsKey(Proof.OTHER))

        return JsonLogic().applyString(logic, data, true).truthy
    }
}