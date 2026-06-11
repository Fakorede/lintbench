package com.android.tools.lint.checks

import com.android.SdkConstants.TAG_MANIFEST
import com.android.annotations.NonNull
import com.android.resources.Density
import com.android.utils.XmlUtils
import com.android.utils.readFully
import com.android.utils.toPath
import com.android.utils.zip.ZipUtil
import com.android.utils.parseDocument
import org.w3c.dom.Element
import java.io.File
import java.nio.file.Files

class AppBundleLocaleChangesDetector : Detector(), ManifestScanner {

    companion object {
        private const val ISSUE_ID = "AppBundleLocaleChanges"
        private const val ISSUE_NAME = "App Bundle Locale Changes Handling"
        private const val ISSUE_EXPLANATION =
            """
            When changing locales at runtime (e.g. to provide an in-app language switcher), the Android App Bundle must be configured to not split by locale or the Play Core library must be used to download additional locales at runtime.
            
            For more information, see https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes
            """
        private val ISSUE = Issue.create(
            id = ISSUE_ID,
            briefDescription = ISSUE_NAME,
            explanation = ISSUE_EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(AppBundleLocaleChangesDetector::class.java, Scope.MANIFEST_SCOPE)
        )
    }

    override fun visitManifest(context: XmlContext, manifestFile: File) {
        val document = parseDocument(manifestFile.readFully(Charsets.UTF_8))
        val applicationNode = XmlUtils.getFirstChildByTagName(document, TAG_MANIFEST)
        if (applicationNode != null) {
            val splitsEnabled = checkSplitsEnabled(context.projectDirectory.toPath())
            if (!splitsEnabled) {
                context.report(
                    ISSUE,
                    manifestFile,
                    context.getLocation(manifestFile),
                    "App Bundle is configured to not split by locale. Ensure you handle runtime locale changes appropriately."
                )
            }
        }
    }

    private fun checkSplitsEnabled(appBundleDir: File): Boolean {
        val bundleFile = appBundleDir.listFiles { file -> file.name.endsWith(".aab") }?.firstOrNull()
        if (bundleFile == null) {
            return false
        }

        ZipUtil.getEntry(bundleFile.toPath(), "base/manifest/AndroidManifest.xml").use { entry ->
            if (entry != null) {
                val manifestContent = Files.readAllLines(entry).joinToString("\n")
                return !manifestContent.contains("android:supportsRtl=\"false\"") &&
                        !manifestContent.contains("<dist:module dist:instant=\"true\">")
            }
        }

        return false
    }
}