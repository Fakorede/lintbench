package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.android.utils.SdkConstants
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), XmlScanner {

    companion object {
        private const val PERMISSION_QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = "If you need to query or interact with other installed apps, you should be using a `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in order to see all installed apps is rarely necessary, and most apps on Google Play are not allowed to have this permission.",
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.MANIFEST_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(SdkConstants.TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        if (nameAttr != null && nameAttr.value == PERMISSION_QUERY_ALL_PACKAGES) {
            context.report(
                ISSUE,
                element,
                context.getLocation(nameAttr),
                "Use `<queries>` instead of the `QUERY_ALL_PACKAGES` permission"
            )
        }
    }
}