package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.MergedManifest
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity

class LeanbackWifiUsageDetector : Detector() {

    companion object {
        private const val LEANBACK_FEATURE = "android.software.leanback"
        private const val WIFI_FEATURE = "android.hardware.wifi"

        private val IMPLEMENTATION = Implementation(
            LeanbackWifiUsageDetector::class.java,
            Scope.MANIFEST_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "LeanbackUsesWifi",
            briefDescription = "Using android.hardware.wifi on TV",
            explanation = """
                WiFi is not required for Android TV and many devices connect to the internet
                via alternative methods, e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes
                to connect to the internet, please modify your Manifest to contain:
                <pre>&lt;uses-feature android:name="android.hardware.wifi" android:required="false" /&gt;</pre>

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
        val project = context.mainProject
        val manifest = project.mergedManifest
        val features = manifest.getUsesFeatures()

        if (!features.any { it.name == LEANBACK_FEATURE && it.required }) {
            return
        }

        val wifiFeatures = features.filter { it.name == WIFI_FEATURE }
        if (wifiFeatures.isEmpty()) {
            return
        }

        val location = createLocation(context, project)
        for (feature in wifiFeatures) {
            if (feature.required) {
                context.report(
                    ISSUE,
                    location,
                    "Using android.hardware.wifi on TV. If your app does not specifically " +
                        "require WiFi, set android:required=\"false\" and use " +
                        "NetworkCapabilities instead."
                )
                return
            }
        }
    }

    private fun isTvApp(manifest: MergedManifest): Boolean {
        return manifest.getUsesFeatures().any { it.name == LEANBACK_FEATURE && it.required }
    }

    private fun createLocation(context: Context, project: Project): Location {
        val manifestFile = project.manifestFiles.firstOrNull()
        return if (manifestFile != null) {
            Location.create(manifestFile)
        } else {
            Location.create(context.file)
        }
    }
}