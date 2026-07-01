package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
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
        private const val QUERY_ALL_PACKAGES_PERMISSION =
            "android.permission.QUERY_ALL_PACKAGES"

        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"

        private const val TARGET_SDK_WITH_PACKAGE_VISIBILITY = 30

        private val GET_INSTALLED_PACKAGES_METHODS = listOf(
            "getInstalledPackages",
            "getInstalledApplications"
        )

        private const val KEY_PERMISSION = "permission"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation =
                """
                If you need to query or interact with other installed apps, you should be \
                using a `<queries>` declaration in your manifest. Using the \
                `QUERY_ALL_PACKAGES` permission in order to see all installed apps is rarely \
                necessary, and most apps on Google Play are not allowed to have this permission.

                See https://g.co/dev/packagevisibility for more details.
                """,
            category = Category.COMPLIANCE,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
                Scope.MANIFEST_SCOPE,
                Scope.JAVA_FILE_SCOPE
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

        val location = context.getValueLocation(nameAttr)
        val message =
            "Most apps can fulfill their use cases using the `<queries>` manifest element " +
                "rather than using the `QUERY_ALL_PACKAGES` permission. This permission is " +
                "not allowed for most apps on Google Play."

        val incident = Incident(ISSUE, element, location, message)
        context.report(incident, targetSdkAtLeast(TARGET_SDK_WITH_PACKAGE_VISIBILITY))
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner — getInstalledPackages / getInstalledApplications call check
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> {
        return GET_INSTALLED_PACKAGES_METHODS
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod
    ) {
        if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) {
            return
        }

        val location = context.getNameLocation(node)
        val message =
            "Using `${method.name}` to get the list of installed packages is not recommended. " +
                "Most apps should instead declare specific package needs in the `<queries>` " +
                "element of the manifest. If you truly need access to all packages, the " +
                "`QUERY_ALL_PACKAGES` permission is required, but note that most apps on " +
                "Google Play are not allowed to use it."

        val incident = Incident(ISSUE, node, location, message)
        context.report(incident, targetSdkAtLeast(TARGET_SDK_WITH_PACKAGE_VISIBILITY))
    }

    // -------------------------------------------------------------------------
    // filterIncident — allow suppression / conditional reporting
    // -------------------------------------------------------------------------

    override fun filterIncident(
        context: JavaContext,
        incident: Incident,
        map: LintMap
    ): Boolean {
        // Preserve any stored data and allow the incident to be reported.
        return true
    }
}