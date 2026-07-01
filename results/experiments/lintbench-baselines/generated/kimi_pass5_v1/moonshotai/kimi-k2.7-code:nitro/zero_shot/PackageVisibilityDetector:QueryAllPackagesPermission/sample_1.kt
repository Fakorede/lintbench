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
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val permissionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (permissionName == "android.permission.QUERY_ALL_PACKAGES" ||
            permissionName == "QUERY_ALL_PACKAGES"
        ) {
            context.report(
                ISSUE_QUERY_ALL_PACKAGES_PERMISSION,
                context.getLocation(element),
                "Using the QUERY_ALL_PACKAGES permission is rarely necessary. " +
                    "Prefer a `<queries>` declaration for package visibility.",
                null
            )
        }
    }

    companion object {
        private const val QUERY_ALL_PACKAGES_PERMISSION = "android.permission.QUERY_ALL_PACKAGES"

        @JvmField
        val ISSUE_QUERY_ALL_PACKAGES_PERMISSION = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using a \
                `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission \
                in order to see all installed apps is rarely necessary, and most apps on Google Play \
                are not allowed to have this permission.
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