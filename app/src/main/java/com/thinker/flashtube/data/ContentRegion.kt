package com.thinker.flashtube.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thinker.flashtube.HomeFeedCache
import com.thinker.flashtube.StreamCache
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale

data class RegionItem(
    val code: String,
    val name: String,
)

object ContentRegion {
    private const val PREFS_NAME = "content_region_prefs"
    private const val KEY_REGION = "region_code"
    const val AUTO_REGION = "AUTO"

    val supportedRegions = listOf(
        RegionItem(AUTO_REGION, "Auto (System Region)"),
        RegionItem("US", "United States"),
        RegionItem("IN", "India"),
        RegionItem("GB", "United Kingdom"),
        RegionItem("CA", "Canada"),
        RegionItem("AU", "Australia"),
        RegionItem("DE", "Germany"),
        RegionItem("FR", "France"),
        RegionItem("JP", "Japan"),
        RegionItem("BR", "Brazil"),
        RegionItem("KR", "South Korea"),
        RegionItem("MX", "Mexico"),
        RegionItem("ES", "Spain"),
        RegionItem("IT", "Italy"),
        RegionItem("ID", "Indonesia"),
        RegionItem("PH", "Philippines"),
        RegionItem("TR", "Turkey"),
        RegionItem("TW", "Taiwan"),
        RegionItem("VN", "Vietnam"),
        RegionItem("NL", "Netherlands"),
        RegionItem("SA", "Saudi Arabia"),
        RegionItem("AE", "United Arab Emirates"),
        RegionItem("PL", "Poland"),
        RegionItem("SE", "Sweden"),
        RegionItem("AR", "Argentina"),
        RegionItem("CL", "Chile"),
        RegionItem("CO", "Colombia"),
        RegionItem("PK", "Pakistan"),
        RegionItem("BD", "Bangladesh"),
        RegionItem("EG", "Egypt"),
        RegionItem("ZA", "South Africa"),
        RegionItem("NG", "Nigeria"),
        RegionItem("TH", "Thailand"),
        RegionItem("MY", "Malaysia"),
        RegionItem("NZ", "New Zealand"),
        RegionItem("IE", "Ireland"),
    )

    private var prefs: SharedPreferences? = null

    /** Selected region setting ("AUTO" or a 2-letter country code like "IN", "US") */
    var code by mutableStateOf(AUTO_REGION)
        private set

    /** Incremented when region changes to trigger reactive feed refresh. */
    var regionGeneration by mutableStateOf(0)
        private set

    /** Effective 2-letter ISO country code passed to InnerTube and NewPipe (e.g., "IN", "US") */
    val currentRegionCode: String
        get() = if (code == AUTO_REGION) {
            Locale.getDefault().country.takeIf { it.isNotBlank() && it.length == 2 }?.uppercase() ?: "US"
        } else {
            code
        }

    val currentLanguageCode: String
        get() = Locale.getDefault().language.takeIf { it.isNotBlank() }?.lowercase() ?: "en"

    val displaySummary: String
        get() {
            val systemCountry = Locale.getDefault().country.takeIf { it.isNotBlank() && it.length == 2 }?.uppercase() ?: "US"
            val matched = supportedRegions.find { it.code == code }
            return if (code == AUTO_REGION) {
                val sysMatched = supportedRegions.find { it.code == systemCountry }?.name ?: systemCountry
                "Auto ($sysMatched)"
            } else {
                matched?.name ?: code
            }
        }

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        code = p.getString(KEY_REGION, AUTO_REGION) ?: AUTO_REGION
        applyToExtractor()
    }

    fun setRegion(newCode: String) {
        val formatted = newCode.uppercase()
        if (code == formatted) return
        code = formatted
        regionGeneration++
        prefs?.edit()?.putString(KEY_REGION, formatted)?.apply()
        applyToExtractor()
        com.thinker.flashtube.Account.clearVisitorData()
        HomeFeedCache.save(emptyList())
        StreamCache.invalidateAll()
    }

    fun applyToExtractor() {
        runCatching {
            val country = currentRegionCode
            val lang = currentLanguageCode
            NewPipe.setupLocalization(Localization(lang, country), ContentCountry(country))
        }
    }
}

typealias RegionSettings = ContentRegion
