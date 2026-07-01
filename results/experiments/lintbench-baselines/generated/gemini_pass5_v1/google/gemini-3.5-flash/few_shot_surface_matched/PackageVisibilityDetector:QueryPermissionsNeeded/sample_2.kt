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

    override fun getApplicableElements(): Collection<String> {
        return listOf("queries")
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        context.project.putClientProperty(KEY_HAS_QUERIES, true)
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("getInstalledPackages", "getInstalledApplications")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.content.pm.PackageManager")) {
            return
        }

        val incident = Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "Consider package visibility requirements when using `PackageManager.${method.name}` on Android 11 and above. This method only returns a filtered list of apps."
        )
        context.report(incident)
    }

    override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
        val project = context.project
        if (project.targetSdk < 30) {
            return false
        }
        if (project.getClientProperty<Boolean>(KEY_HAS_QUERIES) == true) {
            return false
        }
        return true
    }

    companion object {
        private const val KEY_HAS_QUERIES = "PackageVisibilityDetector.hasQueries"

        @JvmField
        val ISSUE = Issue.create(
            id = "QueryPermissionsNeeded",
            briefDescription = "Using APIs affected by query permissions",
            explanation = """
                Apps that target Android 11 cannot query or interact with other installed apps \
                by default. If you need to query or interact with other installed apps, you may need \
                to add a `<queries>` declaration in your manifest.

                As a corollary, the methods `PackageManager#getInstalledPackages` and \
                `PackageManager#getInstalledApplications` will no longer return information about all \
                installed apps. To query specific apps or types of apps, you can use methods like \
                `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            moreInfo = "https://g.co/dev/packagevisibility",
            implementation = Implementation(
                PackageVisibilityDetector::class.java,
                java.util.EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }
}