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

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name == QUERY_ALL_PACKAGES_PERMISSION_VALUE) {
            val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
            val location = if (attribute != null) {
                context.getValueLocation(attribute)
            } else {
                context.getLocation(element)
            }
            context.report(
                issue = ISSUE,
                scope = element,
                location = location,
                message = "Using the `QUERY_ALL_PACKAGES` permission is rarely necessary and " +
                    "most apps on Google Play are not allowed to have this permission. " +
                    "If you need to query or interact with other installed apps, use a " +
                    "`<queries>` declaration in your manifest instead."
            )
        }
    }

    companion object {
        private const val QUERY_ALL_PACKAGES_PERMISSION_VALUE =
            "android.permission.QUERY_ALL_PACKAGES"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be \
                using a `<queries>` declaration in your manifest. Using the \
                QUERY_ALL_PACKAGES permission in order to see all installed apps is rarely \
                necessary, and most apps on Google Play are not allowed to have this \
                permission.
                """,
            category = Category.COMPLIANCE,
            priority = 7,
            severity = Severity.WARNING,
            moreInfo = "https://g.co/dev/packagevisibility",
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}