package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var hasQueryAllPackagesPermission = false
    private var hasQueriesDeclaration = false

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_USES_PERMISSION, TAG_QUERIES)
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        when (element.tagName) {
            TAG_USES_PERMISSION -> {
                val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (name == QUERY_ALL_PACKAGES_PERMISSION) {
                    hasQueryAllPackagesPermission = true
                    val location = context.getElementLocation(element)
                    val message = "Using the QUERY_ALL_PACKAGES permission is rarely necessary. Prefer a `<queries>` declaration."
                    context.report(
                        Incident(
                            ISSUE,
                            element,
                            location,
                            message,
                            LintMap.of(IS_MANIFEST_PERMISSION, true)
                        )
                    )
                }
            }
            TAG_QUERIES -> {
                hasQueriesDeclaration = true
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf(
            "getInstalledPackages",
            "getInstalledPackagesAsUser",
            "getInstalledApplications",
            "getInstalledApplicationsAsUser",
            "queryIntentActivities",
            "queryIntentActivityOptions",
            "queryBroadcastReceivers",
            "queryIntentServices",
            "queryContentProviders"
        )
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, PACKAGE_MANAGER_CLASS)) {
            return
        }
        val location = context.getLocation(node)
        val message = "This call can access information about other installed apps. Prefer a `<queries>` declaration over QUERY_ALL_PACKAGES."
        context.report(
            Incident(
                ISSUE,
                node,
                location,
                message,
                LintMap.of(IS_SOURCE_CALL, true)
            )
        )
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap?): Boolean {
        if (map?.getBoolean(IS_MANIFEST_PERMISSION, false) == true) {
            return true
        }
        if (hasQueriesDeclaration || hasQueryAllPackagesPermission) {
            return false
        }
        return true
    }

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val ATTR_NAME = "name"
        private const val TAG_USES_PERMISSION = "uses-permission"
        private const val TAG_QUERIES = "queries"
        private const val QUERY_ALL_PACKAGES_PERMISSION = "android.permission.QUERY_ALL_PACKAGES"
        private const val PACKAGE_MANAGER_CLASS = "android.content.pm.PackageManager"
        private const val IS_MANIFEST_PERMISSION = "isManifestPermission"
        private const val IS_SOURCE_CALL = "isSourceCall"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryAllPackagesPermission",
            briefDescription = "Using QUERY_ALL_PACKAGES permission",
            explanation = """
                If you need to query or interact with other installed apps, you should be using a `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in order to see all installed apps is rarely necessary, and most apps on Google Play are not allowed to have this permission.

                See https://g.co/dev/packagevisibility for more details.
            """,
            category = Category.COMPLIANCE,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                Scope.MANIFEST_SCOPE,
                Scope.JAVA_FILE_SCOPE
            ),
            androidSpecific = true
        )
    }
}