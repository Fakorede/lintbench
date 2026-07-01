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
        private const val QUERY_ALL_PACKAGES = "android.permission.QUERY_ALL_PACKAGES"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val GET_INSTALLED_PACKAGES = "getInstalledPackages"
        private const val GET_INSTALLED_APPLICATIONS = "getInstalledApplications"

        private const val TARGET_SDK_WITH_PACKAGE_VISIBILITY = 30

        private const val KEY_PERMISSION = "permission"
        private const val KEY_METHOD = "method"

        private val APPLICABLE_METHODS = listOf(
            GET_INSTALLED_PACKAGES,
            GET_INSTALLED_APPLICATIONS
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using the QUERY_ALL_PACKAGES permission",
            explanation =
                """
                If you need to query or interact with other installed apps, you should be using \
                a `<queries>` declaration in your manifest. Using the \
                `QUERY_ALL_PACKAGES` permission in order to see all installed apps is rarely \
                necessary, and most apps on Google Play are not allowed to have this permission.
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
            moreInfo = "https://g.co/dev/packagevisibility",
            androidSpecific = true
        )
    }

    // -------------------------------------------------------------------------
    // XmlScanner — manifest permission declaration
    // -------------------------------------------------------------------------

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
        val permissionName = nameAttr.value
        if (permissionName != QUERY_ALL_PACKAGES) return

        val message =
            "Most apps do not need the `QUERY_ALL_PACKAGES` permission. Use a `<queries>` " +
                "declaration in your manifest to specify which apps you need to interact with. " +
                "See https://g.co/dev/packagevisibility for details."

        val incident = Incident(
            ISSUE,
            element,
            context.getValueLocation(nameAttr),
            message
        )
        val lintMap = LintMap().put(KEY_PERMISSION, true)
        context.report(incident, targetSdkAtLeast(TARGET_SDK_WITH_PACKAGE_VISIBILITY), lintMap)
    }

    // -------------------------------------------------------------------------
    // SourceCodeScanner — calls to getInstalledPackages / getInstalledApplications
    // -------------------------------------------------------------------------

    override fun getApplicableMethodNames(): List<String> = APPLICABLE_METHODS

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) return

        val methodName = method.name
        val message =
            "`PackageManager.$methodName()` returns results filtered by package visibility " +
                "rules on Android 11+. If you need to see packages beyond your own, " +
                "add a `<queries>` element to your manifest or request the " +
                "`QUERY_ALL_PACKAGES` permission (subject to Google Play policy). " +
                "See https://g.co/dev/packagevisibility for details."

        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            message
        )
        val lintMap = LintMap().put(KEY_METHOD, methodName)
        context.report(incident, targetSdkAtLeast(TARGET_SDK_WITH_PACKAGE_VISIBILITY), lintMap)
    }

    // -------------------------------------------------------------------------
    // filterIncident — called when conditional reports are resolved
    // -------------------------------------------------------------------------

    override fun filterIncident(context: JavaContext, incident: Incident, map: LintMap): Boolean {
        // Allow the incident to pass through; we could suppress based on
        // additional project metadata here if needed.
        return true
    }
}