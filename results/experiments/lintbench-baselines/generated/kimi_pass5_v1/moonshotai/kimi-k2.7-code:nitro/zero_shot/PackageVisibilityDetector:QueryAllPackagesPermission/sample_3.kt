package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableElements(): List<String> =
        listOf(SdkConstants.TAG_USES_PERMISSION, SdkConstants.TAG_USES_PERMISSION_SDK_23)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.getAttributeNS(SdkConstants.TOOLS_URI, SdkConstants.ATTR_NODE) ==
            SdkConstants.VALUE_REMOVE
        ) {
            return
        }

        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name == SdkConstants.ANDROID_PERMISSION_QUERY_ALL_PACKAGES) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Using the QUERY_ALL_PACKAGES permission is rarely necessary; prefer a `<queries>` declaration."
            )
        }
    }

    companion object {
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using a
                `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission
                in order to see all installed apps is rarely necessary, and most apps on Google Play
                are not allowed to have this permission.
            """.trimIndent(),
            moreInfo = "https://g.co/dev/packagevisibility",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}