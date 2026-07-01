package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

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

        private const val ANDROID_HARDWARE_WIFI = "android.hardware.wifi"
        private const val USES_FEATURE_TAG = "uses-feature"
        private const val ATTR_NAME = "android:name"
        private const val ATTR_REQUIRED = "android:required"
        private const val TAG_USES_FEATURE = "uses-feature"

        // Uses-feature element for leanback
        private const val LEANBACK_FEATURE = "android.software.leanback"
    }

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject

        // Only check TV / leanback apps
        if (!isLeanbackApp(mainProject)) {
            return
        }

        val mergedManifest = mainProject.mergedManifest ?: return
        val documentElement = mergedManifest.documentElement ?: return

        val usesFeatureElements = documentElement.getElementsByTagName(USES_FEATURE_TAG)

        for (i in 0 until usesFeatureElements.length) {
            val element = usesFeatureElements.item(i) as? Element ?: continue
            val name = element.getAttribute(ATTR_NAME)

            if (name == ANDROID_HARDWARE_WIFI) {
                val required = element.getAttribute(ATTR_REQUIRED)
                // If required is explicitly set to false, this is fine
                if (required.equals("false", ignoreCase = true)) {
                    return
                }
                // required is either true or not set (defaults to true) — report issue
                val location = Location.create(context.mainProject.dir)
                context.report(
                    ISSUE,
                    location,
                    "Requiring `android.hardware.wifi` is not recommended for Android TV apps " +
                        "as many TV devices connect via Ethernet or other means. Consider " +
                        "setting `android:required=\"false\"` if your app does not specifically " +
                        "require WiFi functionality."
                )
                return
            }
        }
    }

    private fun isLeanbackApp(project: com.android.tools.lint.detector.api.Project): Boolean {
        val mergedManifest = project.mergedManifest ?: return false
        val documentElement = mergedManifest.documentElement ?: return false

        val usesFeatureElements = documentElement.getElementsByTagName(TAG_USES_FEATURE)
        for (i in 0 until usesFeatureElements.length) {
            val element = usesFeatureElements.item(i) as? Element ?: continue
            val name = element.getAttribute(ATTR_NAME)
            if (name == LEANBACK_FEATURE) {
                return true
            }
        }
        return false
    }
}