package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.getAttributeNS(ANDROID_URI, ATTR_NAME) == QUERY_ALL_PACKAGES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Using the QUERY_ALL_PACKAGES permission is rarely necessary; prefer a `<queries>` declaration"
            )
        }
    }

    companion object {
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using QUERY_ALL_PACKAGES permission",
            explanation = """
                If your app needs to query or interact with other installed apps, you should use a \
                `<queries>` declaration in your manifest instead of requesting the QUERY_ALL_PACKAGES \
                permission. QUERY_ALL_PACKAGES is rarely necessary and most apps on Google Play are \
                not allowed to have this permission. See \
                https://g.co/dev/packagevisibility for more details.
                """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            moreInfo = "https://g.co/dev/packagevisibility"
        )
    }
}