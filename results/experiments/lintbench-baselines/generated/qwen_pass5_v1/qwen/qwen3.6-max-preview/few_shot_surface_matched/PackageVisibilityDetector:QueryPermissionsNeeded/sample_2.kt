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

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by package visibility",
            explanation = "Apps that target Android 11 (API 30) or higher cannot query or interact with other installed apps by default. " +
                "If you need to query or interact with other installed apps, you may need to add a `<queries>` declaration in your manifest. " +
                "As a corollary, `PackageManager#getInstalledPackages` and `PackageManager#getInstalledApplications` will no longer return information about all installed apps. " +
                "To query specific apps or types of apps, you can use methods like `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(PackageVisibilityDetector::class.java, Scope.JAVA_FILE_SCOPE),
            androidSpecific = true
        )
    }

    private var hasQueriesDeclaration = false

    override fun getApplicableElements(): Collection<String>? {
        return listOf("queries")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName == "queries") {
            hasQueriesDeclaration = true
        }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInClass(method, "android.content.pm.PackageManager")) {
            return
        }

        val message = "Apps targeting Android 11+ cannot query all installed apps by default. " +
            "Consider adding a `<queries>` element to your manifest or using `getPackageInfo`/`queryIntentActivities` instead."

        context.report(
            Incident(ISSUE, node, context.getLocation(node), message)
        )
    }

    override fun filterIncident(context: Context, incident: Incident): Boolean {
        return !hasQueriesDeclaration
    }
}