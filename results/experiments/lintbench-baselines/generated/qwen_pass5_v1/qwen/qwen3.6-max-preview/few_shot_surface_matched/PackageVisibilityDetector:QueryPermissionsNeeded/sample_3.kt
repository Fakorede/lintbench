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

class PackageVisibilityDetector : Detector(), XmlScanner, SourceCodeScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf("manifest")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        // Manifest scanning hook for package visibility checks
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
            return
        }

        val message = "Apps targeting Android 11 and higher cannot query or interact with all installed apps by default. " +
                "Consider adding a <queries> declaration to your manifest, or use alternative APIs like getPackageInfo() or queryIntentActivities()."

        context.report(
            Incident(ISSUE, node, context.getLocation(node), message)
        )
    }

    override fun filterIncident(context: Context, incident: Incident): Boolean {
        return (context.targetSdkVersion ?: 0) >= 30
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by package visibility",
            explanation = "Apps that target Android 11 cannot query or interact with other installed apps by default. " +
                    "If you need to query or interact with other installed apps, you may need to add a `<queries>` declaration in your manifest. " +
                    "As a corollary, the methods `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications` will no longer return information about all installed apps. " +
                    "To query specific apps or types of apps, you can use methods like `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(PackageVisibilityDetector::class.java, Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE)
        )
    }
}