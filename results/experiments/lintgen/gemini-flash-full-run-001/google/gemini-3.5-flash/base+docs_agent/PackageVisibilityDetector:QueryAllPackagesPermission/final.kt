package com.android.tools.lint.checks

import com.android.SdkConstants
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
        return listOf(SdkConstants.TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val permissionName = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (permissionName == "android.permission.QUERY_ALL_PACKAGES") {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Using the `QUERY_ALL_PACKAGES` permission is rarely necessary and most apps on Google Play are not allowed to have this permission"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using a \
                `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in \
                order to see all installed apps is rarely necessary, and most apps on Google Play are \
                not allowed to have this permission.
                """,
            category = Category.SECURITY,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }
}