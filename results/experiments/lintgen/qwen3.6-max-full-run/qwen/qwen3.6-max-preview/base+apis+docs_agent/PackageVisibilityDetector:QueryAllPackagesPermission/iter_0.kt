package com.android.tools.lint.checks

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

    companion object {
        private const val USES_PERMISSION = "uses-permission"
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using a \
                `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in \
                order to see all installed apps is rarely necessary, and most apps on Google Play are \
                not allowed to have this permission.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttribute("android:name")
        if (name == QUERY_ALL_PACKAGES) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Use `<queries>` instead of the `QUERY_ALL_PACKAGES` permission; " +
                        "see https://g.co/dev/packagevisibility"
            )
        }
    }
}