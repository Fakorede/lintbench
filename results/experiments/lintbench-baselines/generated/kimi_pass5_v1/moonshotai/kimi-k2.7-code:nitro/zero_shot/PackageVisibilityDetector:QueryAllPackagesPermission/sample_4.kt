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
import org.w3c.dom.Attr
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (name == QUERY_ALL_PACKAGES) {
            val attr: Attr? = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
            val location = attr?.let { context.getValueLocation(it) }
                ?: context.getElementLocation(element)
            context.report(
                ISSUE,
                location,
                "Using the QUERY_ALL_PACKAGES permission is rarely necessary and may not be allowed on Google Play"
            )
        }
    }

    companion object {
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"

        val ISSUE = Issue.create(
            "QueryAllPackagesPermission",
            "Using the QUERY_ALL_PACKAGES permission",
            "If you need to query or interact with other installed apps, you should be using " +
                "a &lt;queries&gt; declaration in your manifest. Using the " +
                "<code>QUERY_ALL_PACKAGES</code> permission in order to see all installed apps " +
                "is rarely necessary, and most apps on Google Play are not allowed to have this " +
                "permission.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            Implementation(
                PackageVisibilityDetector::class.java,
                Scope.MANIFEST_SCOPE
            ),
            "https://g.co/dev/packagevisibility"
        )
    }
}