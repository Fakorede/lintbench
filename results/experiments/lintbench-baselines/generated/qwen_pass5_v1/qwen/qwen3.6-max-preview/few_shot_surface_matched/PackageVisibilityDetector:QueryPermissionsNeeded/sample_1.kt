package com.android.tools.lint.checks

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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    private var hasQueries = false

    override fun getApplicableElements(): Collection<String> = listOf("queries")

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        hasQueries = true
    }

    override fun getApplicableMethodNames(): List<String> = listOf("getInstalledPackages", "getInstalledApplications")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager", false)) {
            return
        }
        val message = "Using ${method.name}() may not return all installed apps on Android 11+ due to package visibility changes. " +
                "Add a <queries> declaration to your manifest or use alternative APIs like queryIntentActivities()."
        context.report(
            Incident(QUERY_PERMISSIONS_NEEDED, node, context.getLocation(node), message)
        )
    }

    override fun filterIncident(context: Context, incident: Incident): Boolean {
        val targetSdk = context.project.targetSdk ?: 0
        return targetSdk >= 30 && !hasQueries
    }

    companion object {
        @JvmField
        val QUERY_PERMISSIONS_NEEDED = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = "Apps that target Android 11 (API 30) and higher cannot query or interact with all installed apps by default. " +
                "PackageManager#getInstalledPackages and #getInstalledApplications will no longer return information about all installed apps. " +
                "To query specific apps or types of apps, add a <queries> declaration in your manifest or use methods like getPackageInfo or queryIntentActivities.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                java.util.EnumSet.of(Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
            )
        )
    }
}