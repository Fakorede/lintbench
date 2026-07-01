package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> = listOf("uses-permission")

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(ANDROID_URI, "name") ?: return
        if (attr.value == PERMISSION_QUERY_ALL_PACKAGES) {
            context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "Using `$PERMISSION_QUERY_ALL_PACKAGES` is rarely necessary; prefer a `<queries>` declaration instead."
            )
        }
    }

    companion object {
        private const val PERMISSION_QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using a
                `<queries>` declaration in your manifest. Using the `QUERY_ALL_PACKAGES` permission
                in order to see all installed apps is rarely necessary, and most apps on Google Play
                are not allowed to have this permission.

                Reference: https://g.co/dev/packagevisibility
            """.trimIndent(),
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