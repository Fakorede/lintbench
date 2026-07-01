package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), Detector.XmlScanner {

    companion object {
        private const val FEATURE_WIFI = "android.hardware.wifi"

        val ISSUE: Issue = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                Wi-Fi is not required for Android TV and many devices connect to the internet
                via alternative methods, e.g. Ethernet.

                If your app is not focused specifically on Wi-Fi functionality and only wishes
                to connect to the internet, please modify your manifest to contain:
                <uses-feature android:name="android.hardware.wifi" android:required="false" />

                Un-metered or non-roaming connections can be detected in software using
                NetworkCapabilities#NET_CAPABILITY_NOT_METERED and
                NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.
            """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_USES_FEATURE)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name != FEATURE_WIFI) {
            return
        }

        val required =
            element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_REQUIRED)
        if (required == SdkConstants.VALUE_FALSE) {
            return
        }

        if (!context.isTvApp()) {
            return
        }

        context.report(
            ISSUE,
            context.getLocation(element),
            "Using android.hardware.wifi on TV; set android:required=\"false\" unless the app is specifically about Wi-Fi."
        )
    }

    private fun XmlContext.isTvApp(): Boolean {
        val document = this.document ?: return false

        val features = document.getElementsByTagName(SdkConstants.TAG_USES_FEATURE)
        for (i in 0 until features.length) {
            val feature = features.item(i) as? Element ?: continue
            val featureName =
                feature.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (featureName == SdkConstants.FEATURE_LEANBACK) {
                return true
            }
        }

        val categories = document.getElementsByTagName(SdkConstants.TAG_CATEGORY)
        for (i in 0 until categories.length) {
            val category = categories.item(i) as? Element ?: continue
            val categoryName =
                category.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            if (categoryName == SdkConstants.CATEGORY_LEANBACK_LAUNCHER) {
                return true
            }
        }

        return false
    }
}