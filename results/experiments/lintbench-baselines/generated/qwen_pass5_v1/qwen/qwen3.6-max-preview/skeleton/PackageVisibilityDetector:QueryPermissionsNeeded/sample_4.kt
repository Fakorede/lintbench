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
import org.w3c.dom.Element
import java.util.EnumSet

class PackageVisibilityDetector : Detector(), SourceCodeScanner, XmlScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        private const val HAS_QUERIES_TAG = "has_queries_tag"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = "Apps that target Android 11 (API 30) or higher cannot query or interact with other " +
                "installed apps by default. Methods like `getInstalledPackages` and `getInstalledApplications` " +
                "will return filtered results. To query specific apps or types of apps, add a `<queries>` " +
                "declaration in your manifest.",
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf("queries")

    override fun visitElement(context: XmlContext, element: Element) {
        context.project.putClientProperty(HAS_QUERIES_TAG, true)
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(
        "getInstalledPackages",
        "getInstalledApplications"
    )

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.containingClass?.qualifiedName != "android.content.pm.PackageManager") {
            return
        }
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "`${method.name}` may return filtered results on Android 11+ unless a `<queries>` " +
                "declaration is added to the manifest"
        )
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val project = context.project
        // Only warn for apps targeting Android 11 (API 30) or higher
        if ((project.targetSdk ?: 0) < 30) return false
        // Suppress if the manifest already declares <queries>
        if (project.getClientProperty(HAS_QUERIES_TAG) == true) return false
        return true
    }
}