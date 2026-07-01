package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

private const val TAG_USES_FEATURE = "uses-feature"
private const val ATTR_NAME = "android:name"
private const val ATTR_REQUIRED = "android:required"
private const val FEATURE_WIFI = "android.hardware.wifi"
private const val FEATURE_LEANBACK = "android.software.leanback"

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            LeanbackWifiUsageDetector::class.java,
            Scope.MERGED_MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via
                alternative methods, e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to
                connect to the internet, please modify your Manifest to contain:
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val document = context.mainProject.getMergingManifestModel() ?: return
        val features = document.getElementsByTagName(TAG_USES_FEATURE)

        var isLeanbackTv = false
        val wifiFeatures = mutableListOf<org.w3c.dom.Element>()

        for (i in 0 until features.length) {
            val element = features.item(i) as? org.w3c.dom.Element ?: continue
            val name = element.getAttribute(ATTR_NAME)
            when (name) {
                FEATURE_LEANBACK -> if (isRequired(element)) isLeanbackTv = true
                FEATURE_WIFI -> if (isRequired(element)) wifiFeatures.add(element)
            }
        }

        if (!isLeanbackTv || wifiFeatures.isEmpty()) {
            return
        }

        val message = "Using `$FEATURE_WIFI` as required on TV; consider `android:required=\"false\"`"
        for (feature in wifiFeatures) {
            val location = if (context is XmlContext) {
                context.getLocation(feature)
            } else {
                Location.create(context.file)
            }
            context.report(ISSUE, location, message)
        }
    }

    private fun isRequired(element: org.w3c.dom.Element): Boolean {
        val required = element.getAttribute(ATTR_REQUIRED)
        return required.isBlank() || required.toBooleanStrictOrNull() ?: true
    }
}