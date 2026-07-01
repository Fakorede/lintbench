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
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun checkMergedProject(context: Context) {
        val document = context.mainProject.mergedManifest ?: return
        val root = document.documentElement ?: return

        // Check if this is a TV / Leanback app by looking for uses-feature leanback
        var hasLeanback = false
        var wifiElement: Element? = null
        var wifiRequired = true

        var usesFeature = getFirstSubTagByName(root, TAG_USES_FEATURE)
        while (usesFeature != null) {
            val name = usesFeature.getAttributeNS(ANDROID_URI, ATTR_NAME)
            when {
                name == "android.software.leanback" -> {
                    hasLeanback = true
                }
                name == "android.hardware.wifi" -> {
                    wifiElement = usesFeature
                    val requiredAttr = usesFeature.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
                    wifiRequired = requiredAttr.isBlank() || requiredAttr.equals("true", ignoreCase = true)
                }
            }
            usesFeature = getNextTagByName(usesFeature, TAG_USES_FEATURE)
        }

        if (hasLeanback && wifiElement != null && wifiRequired) {
            val xmlContext = context as? XmlContext
            if (xmlContext != null) {
                val location = xmlContext.getElementLocation(wifiElement)
                xmlContext.report(
                    ISSUE,
                    wifiElement,
                    location,
                    "WiFi is not required for Android TV; many TV devices connect via Ethernet. " +
                        "If your app only needs internet access (not WiFi specifically), change to " +
                        "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`. " +
                        "Un-metered or non-roaming connections can be detected using " +
                        "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
                        "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`."
                )
            } else {
                val location = context.project.dir.let {
                    com.android.tools.lint.detector.api.Location.create(it)
                }
                context.report(
                    ISSUE,
                    location,
                    "WiFi is not required for Android TV; many TV devices connect via Ethernet. " +
                        "If your app only needs internet access (not WiFi specifically), change to " +
                        "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`. " +
                        "Un-metered or non-roaming connections can be detected using " +
                        "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
                        "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`."
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation =
                "WiFi is not required for Android TV and many devices connect to the internet " +
                "via alternative methods e.g. Ethernet.\n\n" +
                "If your app is not focused specifically on WiFi functionality and only wishes " +
                "to connect to the internet, please modify your Manifest to contain:\n" +
                "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`\n\n" +
                "Un-metered or non-roaming connections can be detected in software using " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                LeanbackWifiUsageDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}