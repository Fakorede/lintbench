package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element

class PackageVisibilityDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            Scope.MANIFEST_SCOPE
        )

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
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )
    }

    override fun getApplicableElements(): Collection<String> =
        listOf(SdkConstants.TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (nameAttr != null && nameAttr.value == "android.permission.QUERY_ALL_PACKAGES") {
            context.report(
                ISSUE,
                context.getLocation(nameAttr),
                "Use a `<queries>` declaration instead of the QUERY_ALL_PACKAGES permission. " +
                        "Most apps are not allowed to use this permission."
            )
        }
    }
}