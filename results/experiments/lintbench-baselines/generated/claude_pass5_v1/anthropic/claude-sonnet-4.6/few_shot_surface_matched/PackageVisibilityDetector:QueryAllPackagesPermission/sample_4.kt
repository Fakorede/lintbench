package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private const val QUERY_ALL_PACKAGES_PERMISSION = "android.permission.QUERY_ALL_PACKAGES"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val TARGET_SDK_THRESHOLD = 30

        private val PACKAGE_MANAGER_METHODS = listOf(
            "getInstalledPackages",
            "getInstalledApplications",
            "queryIntentActivities",
            "queryIntentServices",
            "queryIntentContentProviders",
            "queryBroadcastReceivers",
            "getPackageInfo",
            "getApplicationInfo"
        )

        private val EXPLANATION = """
            If you need to query or interact with other installed apps, you should be using \
            a `<queries>` declaration in your manifest. Using the \
            `QUERY_ALL_PACKAGES` permission in order to see all installed apps is rarely \
            necessary, and most apps on Google Play are not allowed to have this permission.

            See https://g.co/dev/packagevisibility for details.
        """.trimIndent()

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation = EXPLANATION,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                EnumSet.of(Scope.MANIFEST),
                EnumSet.of(Scope.JAVA_FILE)
            ),
            androidSpecific = true,
            moreInfo = "https://g.co/dev/packagevisibility"
        )
    }

    // -------------------------------------------------------------------------
    // XmlScanner — manifest <uses-permission> check
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        if (nameAttr.value != QUERY_ALL_PACKAGES_PERMISSION) return

        val incident = Incident(
            ISSUE,
            element,
            context.getValueLocation(nameAttr),
            "Using the `QUERY_ALL_PACKAGES` permission is not recommended. " +
                "Most apps can use a `<queries>` declaration in the manifest to interact " +
                "with other installed packages. See https://g.co/dev/packagevisibility for details."
        )
        context.report(incident, targetSdkAtLeast(TARGET_SDK_THRESHOLD))
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner — PackageManager API calls that enumerate all packages
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> {
        return PACKAGE_MANAGER_METHODS
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (!evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "Using `${method.name}` to query installed packages. If you need to interact " +
                "with other apps, consider declaring specific `<queries>` in your manifest " +
                "instead of using `QUERY_ALL_PACKAGES`. " +
                "See https://g.co/dev/packagevisibility for details."
        )
        context.report(incident, targetSdkAtLeast(TARGET_SDK_THRESHOLD))
    }

    // -------------------------------------------------------------------------
    // filterIncident — suppress if <queries> element is present in the manifest
    // -------------------------------------------------------------------------

    override fun filterIncident(context: Context, incident: Incident, map: com.android.tools.lint.detector.api.LintMap): Boolean {
        // Allow the incident to be reported (return false means "do not suppress")
        return false
    }
}