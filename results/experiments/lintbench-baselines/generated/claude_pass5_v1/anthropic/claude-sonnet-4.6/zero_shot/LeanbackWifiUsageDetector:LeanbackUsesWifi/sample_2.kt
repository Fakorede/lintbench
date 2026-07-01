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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private const val HARDWARE_WIFI = "android.hardware.wifi"

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using `android.hardware.wifi` on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via \
                alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to \
                connect to the internet, please modify your Manifest to contain:
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using \
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        ).addMoreInfo(
            "https://developer.android.com/training/tv/start/hardware#wifi-not-required"
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name != HARDWARE_WIFI) {
            return
        }

        val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
        // If android:required="false" is explicitly set, this is fine
        if (requiredAttr != null && requiredAttr.value == "false") {
            return
        }

        // Check if this is a leanback app
        if (!isLeanbackApp(context)) {
            return
        }

        val location = if (requiredAttr != null) {
            context.getLocation(requiredAttr)
        } else {
            context.getLocation(element)
        }

        context.report(
            issue = ISSUE,
            element = element,
            location = location,
            message = "Requiring `android.hardware.wifi` is not recommended for Android TV apps " +
                "as many TV devices connect via Ethernet. Consider setting " +
                "`android:required=\"false\"` if your app only needs internet connectivity."
        )
    }

    private fun isLeanbackApp(context: XmlContext): Boolean {
        val document = context.document ?: return false
        val manifestElement = document.documentElement ?: return false
        val applicationElements = manifestElement.getElementsByTagName("application")
        if (applicationElements.length == 0) {
            return false
        }

        // Check for uses-feature leanback
        val usesFeatureElements = manifestElement.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until usesFeatureElements.length) {
            val featureElement = usesFeatureElements.item(i) as? Element ?: continue
            val featureName = featureElement.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (featureName == "android.software.leanback") {
                return true
            }
        }

        // Check for leanback launcher intent filter
        val activityElements = document.getElementsByTagName("activity")
        for (i in 0 until activityElements.length) {
            val activity = activityElements.item(i) as? Element ?: continue
            val intentFilters = activity.getElementsByTagName("intent-filter")
            for (j in 0 until intentFilters.length) {
                val intentFilter = intentFilters.item(j) as? Element ?: continue
                val categories = intentFilter.getElementsByTagName("category")
                for (k in 0 until categories.length) {
                    val category = categories.item(k) as? Element ?: continue
                    val categoryName = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
                    if (categoryName == "android.intent.category.LEANBACK_LAUNCHER") {
                        return true
                    }
                }
            }
        }

        return false
    }
}