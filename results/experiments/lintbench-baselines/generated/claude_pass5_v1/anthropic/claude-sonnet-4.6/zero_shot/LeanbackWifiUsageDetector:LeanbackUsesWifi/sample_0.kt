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
import java.util.EnumSet

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    companion object {
        private const val HARDWARE_WIFI = "android.hardware.wifi"

        @JvmField
        val ISSUE: Issue = Issue.create(
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
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                EnumSet.of(Scope.MANIFEST)
            )
        )
    }

    /** Tracks whether we have seen a leanback uses-feature declaration */
    private var hasLeanbackFeature = false

    /** Tracks wifi uses-feature elements that may need to be reported */
    private data class WifiFeatureEntry(
        val element: Element,
        val context: XmlContext,
        val isRequired: Boolean
    )

    private val wifiFeatureEntries = mutableListOf<WifiFeatureEntry>()

    override fun beforeCheckFile(context: Context) {
        hasLeanbackFeature = false
        wifiFeatureEntries.clear()
    }

    override fun afterCheckFile(context: Context) {
        if (hasLeanbackFeature) {
            for (entry in wifiFeatureEntries) {
                if (entry.isRequired) {
                    val fix = entry.context.document?.let {
                        fix().set()
                            .android()
                            .attribute(ATTR_REQUIRED)
                            .value("false")
                            .build()
                    }
                    entry.context.report(
                        ISSUE,
                        entry.element,
                        entry.context.getNameLocation(entry.element),
                        "Requiring the WiFi feature (`android.hardware.wifi`) is not recommended " +
                                "for Android TV apps as many TV devices connect to the internet " +
                                "via Ethernet. Consider setting " +
                                "`android:required=\"false\"` and detecting network capabilities " +
                                "using `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
                                "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING` instead.",
                        fix
                    )
                }
            }
        }
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_FEATURE)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: return

        when {
            name == "android.software.leanback" -> {
                hasLeanbackFeature = true
            }
            name == HARDWARE_WIFI -> {
                val requiredAttr = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                // If required is explicitly set to "false", it's fine.
                // If it's "true" or absent (defaults to true), record it.
                val isRequired = requiredAttr.isNullOrEmpty() || requiredAttr == "true"
                wifiFeatureEntries.add(WifiFeatureEntry(element, context, isRequired))
            }
        }
    }
}