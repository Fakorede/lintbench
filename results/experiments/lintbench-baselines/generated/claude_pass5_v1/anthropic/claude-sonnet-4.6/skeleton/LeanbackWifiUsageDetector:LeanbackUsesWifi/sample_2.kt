package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            LeanbackWifiUsageDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via \
                alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to \
                connect to the internet, please modify your Manifest to contain: \
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using \
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )

        private const val HARDWARE_WIFI = "android.hardware.wifi"
        private const val USES_FEATURE_LEANBACK = "android.software.leanback"
        private const val KEY_WIFI_REQUIRED = "wifiRequired"
        private const val KEY_HAS_LEANBACK = "hasLeanback"
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return

        when (name) {
            USES_FEATURE_LEANBACK -> {
                val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                // leanback feature is present (required=true or not specified means it's a TV app)
                if (required != "false") {
                    context.getPartialResults(ISSUE).map().put(KEY_HAS_LEANBACK, true)
                }
            }
            HARDWARE_WIFI -> {
                val required = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                // wifi is required (required=true or not specified)
                if (required != "false") {
                    val location = context.getLocation(element)
                    context.getPartialResults(ISSUE).map()
                        .put(KEY_WIFI_REQUIRED, true)
                        .put("location", location)
                }
            }
        }
    }

    override fun checkMergedProject(context: Context) {
        val partialResults = context.getPartialResults(ISSUE).maps()

        var hasLeanback = false
        var wifiRequired = false
        var wifiLocation: com.android.tools.lint.detector.api.Location? = null

        for (map in partialResults) {
            if (map.getBoolean(KEY_HAS_LEANBACK, false) == true) {
                hasLeanback = true
            }
            if (map.getBoolean(KEY_WIFI_REQUIRED, false) == true) {
                wifiRequired = true
                val loc = map.getLocation("location")
                if (loc != null) {
                    wifiLocation = loc
                }
            }
        }

        if (hasLeanback && wifiRequired) {
            val location = wifiLocation ?: context.project.manifestFiles.firstOrNull()?.let {
                com.android.tools.lint.detector.api.Location.create(it)
            } ?: return

            context.report(
                issue = ISSUE,
                location = location,
                message = "Requiring `android.hardware.wifi` is not recommended for Android TV " +
                    "apps as many TV devices connect to the internet via Ethernet. Consider " +
                    "setting `android:required=\"false\"` for this feature.",
            )
        }
    }
}