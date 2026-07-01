package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
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
import com.android.utils.XmlUtils
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_FEATURE)

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (nameAttr.value != HARDWARE_WIFI) return

        val requiredAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
        if (requiredAttr != null && requiredAttr.value == "false") return

        // Only flag if the manifest also uses leanback (TV app)
        val document = element.ownerDocument ?: return
        val manifestElement = document.documentElement ?: return
        if (!hasLeanbackFeature(manifestElement)) return

        val location = context.getValueLocation(nameAttr)
        context.report(
            ISSUE,
            element,
            location,
            "WiFi is not required for Android TV; many TV devices connect via Ethernet. " +
                "If your app only needs internet access (not WiFi specifically), change this to " +
                "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`. " +
                "Un-metered or non-roaming connections can be detected using " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
                "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`."
        )
    }

    private fun hasLeanbackFeature(manifestElement: Element): Boolean {
        var child = XmlUtils.getFirstSubTagByName(manifestElement, TAG_USES_FEATURE)
        while (child != null) {
            val name = child.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name == LEANBACK_FEATURE) return true
            child = XmlUtils.getNextTagByName(child, TAG_USES_FEATURE)
        }
        return false
    }

    override fun checkMergedProject(context: Context) {
        val mainProject = context.mainProject
        val mergedManifest = mainProject.mergedManifest ?: return
        val documentElement = mergedManifest.documentElement ?: return

        if (!hasLeanbackFeature(documentElement)) return

        var usesFeature = XmlUtils.getFirstSubTagByName(documentElement, TAG_USES_FEATURE)
        while (usesFeature != null) {
            val nameAttr = usesFeature.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
            if (nameAttr != null && nameAttr.value == HARDWARE_WIFI) {
                val requiredAttr = usesFeature.getAttributeNodeNS(ANDROID_URI, ATTR_REQUIRED)
                if (requiredAttr == null || requiredAttr.value != "false") {
                    context.report(
                        ISSUE,
                        context.getLocation(usesFeature),
                        "WiFi is not required for Android TV; many TV devices connect via Ethernet. " +
                            "If your app only needs internet access (not WiFi specifically), change this to " +
                            "`<uses-feature android:name=\"android.hardware.wifi\" android:required=\"false\" />`. " +
                            "Un-metered or non-roaming connections can be detected using " +
                            "`NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and " +
                            "`NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING`."
                    )
                }
            }
            usesFeature = XmlUtils.getNextTagByName(usesFeature, TAG_USES_FEATURE)
        }
    }

    companion object {
        private const val HARDWARE_WIFI = "android.hardware.wifi"
        private const val LEANBACK_FEATURE = "android.software.leanback"
        private const val ATTR_REQUIRED = "required"

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using `android.hardware.wifi` on TV",
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