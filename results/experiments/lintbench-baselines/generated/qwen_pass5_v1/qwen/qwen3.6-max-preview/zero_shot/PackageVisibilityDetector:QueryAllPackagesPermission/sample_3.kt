package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.ANDROID_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class PackageVisibilityDetector : ResourceXmlDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf("uses-permission")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttribute = element.getAttributeNodeNS(ANDROID_URI, "name")
        if (nameAttribute != null && nameAttribute.value == "android.permission.QUERY_ALL_PACKAGES") {
            context.report(
                ISSUE,
                context.getLocation(nameAttribute),
                "Use of `QUERY_ALL_PACKAGES` permission is discouraged. " +
                    "Consider using a `<queries>` declaration instead."
            )
        }
    }
}