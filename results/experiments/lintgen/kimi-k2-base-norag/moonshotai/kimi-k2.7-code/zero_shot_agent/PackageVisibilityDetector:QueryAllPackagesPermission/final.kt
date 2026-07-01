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

    override fun getApplicableElements(): Collection<String> = listOf(SdkConstants.TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (name == QUERY_ALL_PACKAGES) {
            context.report(
                ISSUE,
                context.getElementLocation(element),
                "Use of QUERY_ALL_PACKAGES permission is discouraged; prefer `<queries>` manifest declarations instead."
            )
        }
    }

    companion object {
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should use a \
                `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission \
                in order to see all installed apps is rarely necessary, and most apps on Google Play \
                are not allowed to have this permission.
            """,
            moreInfo = "https://g.co/dev/packagevisibility",
            category = Category.SECURITY,
            priority = 9,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}