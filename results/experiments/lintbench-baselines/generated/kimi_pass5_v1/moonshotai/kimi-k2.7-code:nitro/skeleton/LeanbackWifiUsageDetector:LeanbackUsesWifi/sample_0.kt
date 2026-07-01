package com.android.tools.lint.checks

import com.android.tools.lint.client.api.XmlUtils
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val FEATURE_WIFI = "android.hardware.wifi"
private const val CATEGORY_LEANBACK_LAUNCHER = "android.intent.category.LEANBACK_LAUNCHER"

private const val MESSAGE =
    "If your app is not focused specifically on WiFi functionality and only wishes to connect to the internet, please modify your Manifest to contain: `<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`"

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            LeanbackWifiUsageDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet via
                alternative methods such as Ethernet. If your app is not specifically focused on
                WiFi functionality and only needs to reach the internet, declare the WiFi hardware
                feature as not required with
                <uses-feature android:name="android.hardware.wifi" android:required="false" />.

                Un-metered or non-roaming connections can be detected in software using
                NetworkCapabilities#NET_CAPABILITY_NOT_METERED and
                NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun checkMergedProject(context: Context) {
        val manifest: java.io.File = context.mainProject.mergedManifest ?: return
        val document = XmlUtils.parseDocumentSilently(manifest, true) ?: return

        if (!isLeanbackTvApp(document)) {
            return
        }

        val features = document.getElementsByTagName("uses-feature")
        for (i in 0 until features.length) {
            val feature = features.item(i) as? org.w3c.dom.Element ?: continue
            val name = feature.getAttributeNS(ANDROID_URI, "name")
            if (name != FEATURE_WIFI) {
                continue
            }

            val required = feature.getAttributeNS(ANDROID_URI, "required")
            if (required == "false") {
                continue
            }

            val location = Location.create(manifest, feature)
            context.report(ISSUE, location, MESSAGE)
        }
    }

    private fun isLeanbackTvApp(document: org.w3c.dom.Document): Boolean {
        val categories = document.getElementsByTagName("category")
        for (i in 0 until categories.length) {
            val category = categories.item(i) as? org.w3c.dom.Element ?: continue
            val name = category.getAttributeNS(ANDROID_URI, "name")
            if (name == CATEGORY_LEANBACK_LAUNCHER) {
                return true
            }
        }
        return false
    }
}