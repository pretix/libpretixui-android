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
            { "!=" : [ { "var": "VACC.occurence_days_since" }, null ] },
            { "<=" : [ { "var": "VACC.occurence_days_since" }, 180 ] }
        ]},
    
        { "var" : "CURED" },
    
        {"and" : [
            { "var" : "VACC" },
            { "var": "VACC.isBooster" }
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
        val data = mutableMapOf<String, Any>()

        if (results.containsKey(Proof.VACC)) {
            data.put("VACC", true)
            val cert = results[Proof.VACC]!!.dgcEntry
            if (cert is Vaccination) {
                data.put("VACC.targetDisease", cert.targetDisease)
                data.put("VACC.vaccineCode", cert.vaccineCode)
                data.put("VACC.product", cert.product)
                data.put("VACC.manufacturer", cert.manufacturer)
                data.put("VACC.doseNumber", cert.doseNumber)
                data.put("VACC.totalSerialDoses", cert.totalSerialDoses)
                data.put("VACC.occurence_days_since", ChronoUnit.DAYS.between(cert.occurrence, LocalDate.now()))
                data.put("VACC.country", cert.country)
                data.put("VACC.certificateIssuer", cert.certificateIssuer)
                data.put("VACC.isComplete", cert.isComplete)
                data.put("VACC.isCompleteSingleDose", cert.isCompleteSingleDose)
                data.put("VACC.isBooster", cert.isBooster)
                data.put("VACC.hasFullProtectionAfterRecovery", cert.hasFullProtectionAfterRecovery)
                data.put("VACC.hasFullProtection", cert.hasFullProtection)
            }
        } else {
            data.put("VACC", false)
        }

        if (results.containsKey(Proof.CURED)) {
            data.put("CURED", true)
            val cert = results[Proof.CURED]!!.dgcEntry
            if (cert is Recovery) {
                data.put("CURED.targetDisease", cert.targetDisease)
                data.put("CURED.firstResult_days_since", ChronoUnit.DAYS.between(cert.firstResult, LocalDate.now()))
                data.put("CURED.validFrom_days_since", ChronoUnit.DAYS.between(cert.validFrom, LocalDate.now()))
                data.put("CURED.validUntil_days_until", ChronoUnit.DAYS.between(LocalDate.now(), cert.validUntil))
                data.put("CURED.country", cert.country)
                data.put("CURED.certificateIssuer", cert.certificateIssuer)
            }
        } else {
            data.put("CURED", false)
        }

        if (results.containsKey(Proof.TESTED_PCR)) {
            data.put("TESTED_PCR", true)
            val cert = results[Proof.VACC]!!.dgcEntry
            if (cert is TestCert) {
                data.put("TESTED_PCR.targetDisease", cert.targetDisease)
                data.put("TESTED_PCR.testType", cert.testType)
                data.put("TESTED_PCR.testName", cert.testName ?: "")
                data.put("TESTED_PCR.manufacturer", cert.manufacturer ?: "")
                data.put("TESTED_PCR.sampleCollection_hours_since", ChronoUnit.HOURS.between(cert.sampleCollection, ZonedDateTime.now()))
                data.put("TESTED_PCR.testResult", cert.testResult)
                data.put("TESTED_PCR.testingCenter", cert.testingCenter)
                data.put("TESTED_PCR.country", cert.country)
                data.put("TESTED_PCR.certificateIssuer", cert.certificateIssuer)
                data.put("TESTED_PCR.isPositive", cert.isPositive)
            }
        } else {
            data.put("TESTED_PCR", false)
        }

        if (results.containsKey(Proof.TESTED_AG_UNKNOWN)) {
            data.put("TESTED_AG_UNKNOWN", true)
            val cert = results[Proof.VACC]!!.dgcEntry
            if (cert is TestCert) {
                data.put("TESTED_AG_UNKNOWN.targetDisease", cert.targetDisease)
                data.put("TESTED_AG_UNKNOWN.testType", cert.testType)
                data.put("TESTED_AG_UNKNOWN.testName", cert.testName ?: "")
                data.put("TESTED_AG_UNKNOWN.manufacturer", cert.manufacturer ?: "")
                data.put("TESTED_AG_UNKNOWN.sampleCollection_hours_since", ChronoUnit.HOURS.between(cert.sampleCollection, ZonedDateTime.now()))
                data.put("TESTED_AG_UNKNOWN.testResult", cert.testResult)
                data.put("TESTED_AG_UNKNOWN.testingCenter", cert.testingCenter)
                data.put("TESTED_AG_UNKNOWN.country", cert.country)
                data.put("TESTED_AG_UNKNOWN.certificateIssuer", cert.certificateIssuer)
                data.put("TESTED_AG_UNKNOWN.isPositive", cert.isPositive)
            }
        } else {
            data.put("TESTED_AG_UNKNOWN", false)
        }

        if (results.containsKey(Proof.OTHER)) {
            data.put("OTHER", true)
        } else {
            data.put("OTHER", false)
        }

        return JsonLogic().applyString(logic, data, true).truthy
    }
}